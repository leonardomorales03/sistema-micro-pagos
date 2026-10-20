package com.micropay.backend.application.services;

import com.micropay.backend.domain.entities.Plan;
import com.micropay.backend.domain.entities.Subscription;
import com.micropay.backend.domain.entities.User;
import com.micropay.backend.domain.entities.Wallet;
import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.exceptions.UserNotFoundException;
import com.micropay.backend.domain.exceptions.WalletNotFoundException;
import com.micropay.backend.domain.ports.out.PlanRepository;
import com.micropay.backend.domain.ports.out.SubscriptionRepository;
import com.micropay.backend.domain.ports.out.UserRepository;
import com.micropay.backend.domain.ports.out.WalletRepository;
import com.micropay.backend.domain.valueobjects.PlanId;
import com.micropay.backend.domain.valueobjects.SubscriptionId;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Casos de uso de Subscriptions (Requirement 22.2, 22.6) — T20.
 * El cobro recurrente lo ejecuta {@link SubscriptionBillingService} (scheduler).
 */
@Service
@Transactional(readOnly = true)
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository subscriptions;
    private final PlanRepository plans;
    private final WalletRepository wallets;
    private final UserRepository users;

    public SubscriptionService(SubscriptionRepository subscriptions,
                               PlanRepository plans,
                               WalletRepository wallets,
                               UserRepository users) {
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.wallets = wallets;
        this.users = users;
    }

    // ────────────────── 1. Suscribirse a un Plan (Requisito 22.2) ──────────────────

    @Transactional(rollbackFor = Exception.class)
    public Subscription subscribe(UserId subscriberId, PlanId planId, WalletId walletId) {
        Plan plan = plans.findById(planId)
                .orElseThrow(() -> new BusinessRuleViolationException("PLAN_NOT_FOUND",
                        "Plan " + planId + " no encontrado"));
        if (!plan.isActive()) {
            throw new BusinessRuleViolationException("PLAN_NOT_ACTIVE",
                    "El plan %s está archivado y no acepta nuevas suscripciones".formatted(planId));
        }
        if (plan.merchantUserId().equals(subscriberId)) {
            throw new BusinessRuleViolationException("SUBSCRIPTION_SELF_SUBSCRIBE",
                    "El comercio no puede suscribirse a su propio plan " + planId);
        }

        User subscriber = users.findById(subscriberId)
                .orElseThrow(() -> new UserNotFoundException(subscriberId.uuid()));
        subscriber.assertCanOperate("suscribirse a un plan");

        Wallet wallet = wallets.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId.uuid()));
        if (!wallet.userId().equals(subscriberId)) {
            throw new BusinessRuleViolationException("WALLET_NOT_OWNED_BY_USER",
                    "Wallet %s no pertenece al suscriptor %s".formatted(walletId, subscriberId));
        }
        if (wallet.status() != Wallet.Status.ACTIVE) {
            throw new BusinessRuleViolationException("WALLET_NOT_ACTIVE",
                    "Wallet %s estado %s no permite suscribirse".formatted(walletId, wallet.status()));
        }
        if (!wallet.currency().equals(plan.amount().currency())) {
            throw new BusinessRuleViolationException("CURRENCY_MISMATCH",
                    "Wallet currency=%s vs plan currency=%s"
                            .formatted(wallet.currency(), plan.amount().currency()));
        }

        Subscription subscription = Subscription.start(plan, subscriberId, walletId);
        subscription = subscriptions.save(subscription);

        log.info("[SUBSCRIPTION] Creada id={} plan={} subscriber={} status={} nextBilling={}",
                subscription.id(), planId, subscriberId, subscription.status(), subscription.nextBillingDate());
        return subscription;
    }

    // ────────────────── 2. Gestión (cancelar/pausar/reanudar) ──────────────────

    @Transactional(rollbackFor = Exception.class)
    public Subscription cancel(UserId actorId, SubscriptionId id) {
        Subscription sub = getOwned(actorId, id);
        sub.cancel();
        Subscription saved = subscriptions.save(sub);
        log.info("[SUBSCRIPTION] Cancelada id={} actor={}", saved.id(), actorId);
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public Subscription pause(UserId actorId, SubscriptionId id) {
        Subscription sub = getOwned(actorId, id);
        sub.pause();
        Subscription saved = subscriptions.save(sub);
        log.info("[SUBSCRIPTION] Pausada id={} actor={}", saved.id(), actorId);
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public Subscription resume(UserId actorId, SubscriptionId id) {
        Subscription sub = getOwned(actorId, id);
        sub.resume();
        Subscription saved = subscriptions.save(sub);
        log.info("[SUBSCRIPTION] Reanudada id={} actor={}", saved.id(), actorId);
        return saved;
    }

    // ────────────────── 3. Consultas ──────────────────

    public List<Subscription> listBySubscriber(UserId subscriberId, int page, int pageSize) {
        return subscriptions.listBySubscriber(subscriberId, page, pageSize);
    }

    public Subscription get(SubscriptionId id) {
        return subscriptions.findById(id)
                .orElseThrow(() -> new BusinessRuleViolationException("SUBSCRIPTION_NOT_FOUND",
                        "Subscription " + id + " no encontrada"));
    }

    /** Lista suscriptores de un plan propio del comercio. */
    public List<Subscription> listByPlan(UserId merchantId, PlanId planId, int page, int pageSize) {
        Plan plan = plans.findById(planId)
                .orElseThrow(() -> new BusinessRuleViolationException("PLAN_NOT_FOUND",
                        "Plan " + planId + " no encontrado"));
        if (!plan.merchantUserId().equals(merchantId)) {
            throw new BusinessRuleViolationException("PLAN_NOT_OWNED_BY_MERCHANT",
                    "Plan %s no pertenece al comercio %s".formatted(planId, merchantId));
        }
        return subscriptions.listByPlan(planId, page, pageSize);
    }

    private Subscription getOwned(UserId actorId, SubscriptionId id) {
        Subscription sub = get(id);
        if (!sub.subscriberUserId().equals(actorId)) {
            throw new BusinessRuleViolationException("SUBSCRIPTION_NOT_OWNED_BY_SUBSCRIBER",
                    "Subscription %s no pertenece al usuario %s".formatted(id, actorId));
        }
        return sub;
    }
}
