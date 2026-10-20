package com.micropay.backend.application.services;

import com.micropay.backend.domain.entities.Plan;
import com.micropay.backend.domain.entities.Subscription;
import com.micropay.backend.domain.entities.Transaction;
import com.micropay.backend.domain.entities.Wallet;
import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.ports.out.NotificationAdapter;
import com.micropay.backend.domain.ports.out.PlanRepository;
import com.micropay.backend.domain.ports.out.SubscriptionRepository;
import com.micropay.backend.domain.ports.out.WalletRepository;
import com.micropay.backend.domain.valueobjects.FeeOperation;
import com.micropay.backend.domain.valueobjects.SubscriptionId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Facturación recurrente de suscripciones (Requirement 22.3, 22.4, 22.5) — T20.
 *
 * Cada suscripción se procesa en su propia transacción (REQUIRES_NEW) para que el fallo de una
 * no revierta el resto del lote. El cobro reutiliza {@link TransactionService#transferP2P} con
 * {@link FeeOperation#SUBSCRIPTION} (locks Redis + Spring Retry + ledger de doble entrada + comisión).
 *
 * Política de dunning: ante un cobro fallido se reprograma el siguiente intento según
 * {@code micropay.subscriptions.dunning.retry-days} (p. ej. +1, +3, +7 días); agotados los
 * reintentos la suscripción pasa a CANCELED.
 */
@Service
public class SubscriptionBillingService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionBillingService.class);

    private final SubscriptionRepository subscriptions;
    private final PlanRepository plans;
    private final WalletRepository wallets;
    private final TransactionService transactionService;
    private final NotificationAdapter notifications;
    private final TransactionTemplate billingTx;

    /** Días de reintento tras cada fallo (Requirement 22.5). */
    @Value("${micropay.subscriptions.dunning.retry-days:1,3,7}")
    private List<Integer> dunningRetryDays;

    /** Máximo de suscripciones procesadas por ciclo. */
    @Value("${micropay.subscriptions.billing.batch-size:100}")
    private int batchSize;

    public SubscriptionBillingService(SubscriptionRepository subscriptions,
                                      PlanRepository plans,
                                      WalletRepository wallets,
                                      TransactionService transactionService,
                                      NotificationAdapter notifications,
                                      PlatformTransactionManager transactionManager) {
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.wallets = wallets;
        this.transactionService = transactionService;
        this.notifications = notifications;
        this.billingTx = new TransactionTemplate(transactionManager);
        this.billingTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Resumen de una ejecución del ciclo de facturación. */
    public record BillingRunSummary(int scanned, int succeeded, int failed, int canceled) {}

    private enum Outcome { SUCCESS, FAILED, CANCELED }

    /**
     * Ejecuta el ciclo de facturación: busca suscripciones vencidas y cobra cada una.
     * NO es transaccional a nivel de lote: cada suscripción se cobra de forma independiente.
     */
    public BillingRunSummary runBillingCycle() {
        Instant now = Instant.now();
        List<Subscription> due = subscriptions.findDueForBilling(now, batchSize);

        int ok = 0, failed = 0, canceled = 0;
        for (Subscription sub : due) {
            Outcome outcome;
            try {
                billingTx.executeWithoutResult(st -> billOne(sub.id(), now));
                outcome = Outcome.SUCCESS;
            } catch (Exception e) {
                log.warn("[SUBSCRIPTION_BILLING] Cobro falló sub={}: {}", sub.id(), rootMessage(e));
                outcome = recordFailure(sub.id(), now, rootMessage(e));
            }
            switch (outcome) {
                case SUCCESS -> ok++;
                case FAILED -> failed++;
                case CANCELED -> canceled++;
            }
        }

        BillingRunSummary summary = new BillingRunSummary(due.size(), ok, failed, canceled);
        log.info("[SUBSCRIPTION_BILLING] Ciclo completado {}", summary);
        return summary;
    }

    // ────────────────── Cobro de una suscripción (éxito) ──────────────────

    private void billOne(SubscriptionId id, Instant now) {
        Subscription sub = subscriptions.findById(id).orElse(null);
        if (sub == null) return;
        if (!sub.isDueAt(now)) return; // pudo cobrarse en otra instancia (ShedLock lo evita, guardia extra)

        Plan plan = plans.findById(sub.planId())
                .orElseThrow(() -> new BusinessRuleViolationException("PLAN_NOT_FOUND",
                        "Plan " + sub.planId() + " no encontrado para subscription " + id));

        Wallet merchantWallet = wallets
                .findByUserAndCurrency(plan.merchantUserId(), plan.amount().currency())
                .orElseThrow(() -> new BusinessRuleViolationException("SUBSCRIPTION_MERCHANT_WALLET_MISSING",
                        "El comercio %s no tiene wallet en %s"
                                .formatted(plan.merchantUserId(), plan.amount().currency())));

        // Cobro: débito del suscriptor → crédito al comercio (fee SUBSCRIPTION según FeeEngine).
        Transaction tx = transactionService.transferP2P(
                sub.subscriberWalletId(),
                merchantWallet.id(),
                plan.amount(),
                sub.subscriberUserId(),
                "Subscription " + sub.id() + " plan " + plan.name(),
                FeeOperation.SUBSCRIPTION);

        sub.advancePeriod(plan.interval(), plan.intervalCount());
        Subscription saved = subscriptions.save(sub);

        registerAfterCommit(() -> {
            notify(sub.subscriberUserId(), "SUBSCRIPTION_CHARGED", Map.of(
                    "subscription_id", saved.id().toString(),
                    "plan_id", plan.id().toString(),
                    "tx_id", tx.id().toString(),
                    "amount", plan.amount().toString(),
                    "next_billing_date", saved.nextBillingDate().toString()));
            notify(plan.merchantUserId(), "SUBSCRIPTION_PAYMENT_RECEIVED", Map.of(
                    "subscription_id", saved.id().toString(),
                    "plan_id", plan.id().toString(),
                    "tx_id", tx.id().toString(),
                    "amount", plan.amount().toString(),
                    "subscriber_user_id", sub.subscriberUserId().toString()));
        });

        log.info("[SUBSCRIPTION_BILLING] ✅ Cobro OK sub={} tx={} next={}",
                saved.id(), tx.id(), saved.nextBillingDate());
    }

    // ────────────────── Registro de fallo + dunning ──────────────────

    private Outcome recordFailure(SubscriptionId id, Instant now, String reason) {
        return billingTx.execute(st -> {
            Subscription sub = subscriptions.findById(id).orElse(null);
            if (sub == null) return Outcome.FAILED;

            int attemptsAfter = sub.dunningAttempts() + 1;
            Outcome outcome;
            String template;
            if (attemptsAfter > dunningRetryDays.size()) {
                sub.cancel();
                outcome = Outcome.CANCELED;
                template = "SUBSCRIPTION_CANCELED";
                log.warn("[SUBSCRIPTION_BILLING] Reintentos agotados → CANCELED sub={}", id);
            } else {
                int days = Math.max(1, dunningRetryDays.get(attemptsAfter - 1));
                Instant nextAttempt = now.plus(Duration.ofDays(days));
                sub.registerBillingFailure(reason, nextAttempt);
                outcome = Outcome.FAILED;
                template = "SUBSCRIPTION_PAYMENT_FAILED";
                log.info("[SUBSCRIPTION_BILLING] Dunning sub={} attempt={} next={}", id, attemptsAfter, nextAttempt);
            }

            Subscription saved = subscriptions.save(sub);
            final String tpl = template;
            registerAfterCommit(() -> notify(saved.subscriberUserId(), tpl, Map.of(
                    "subscription_id", saved.id().toString(),
                    "status", saved.status().name(),
                    "attempts", String.valueOf(saved.dunningAttempts()),
                    "reason", reason,
                    "next_billing_date", saved.nextBillingDate().toString())));
            return outcome;
        });
    }

    // ────────────────── Helpers ──────────────────

    private String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }

    private void notify(com.micropay.backend.domain.valueobjects.UserId userId, String template, Map<String, Object> payload) {
        try {
            notifications.send(new NotificationAdapter.NotificationRequest(
                    userId, NotificationAdapter.Channel.PUSH, template, payload));
        } catch (Exception e) {
            log.warn("No se pudo enviar notificación {} a user {}: {}", template, userId, e.getMessage());
        }
    }

    private void registerAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { action.run(); }
            });
        } else {
            action.run();
        }
    }
}
