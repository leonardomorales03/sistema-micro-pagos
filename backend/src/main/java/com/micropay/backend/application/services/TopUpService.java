package com.micropay.backend.application.services;

import com.micropay.backend.domain.entities.AccountMove;
import com.micropay.backend.domain.entities.TopUp;
import com.micropay.backend.domain.entities.Transaction;
import com.micropay.backend.domain.entities.Wallet;
import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.exceptions.WalletNotFoundException;
import com.micropay.backend.domain.ports.out.NotificationAdapter;
import com.micropay.backend.domain.ports.out.PaymentProviderAdapter;
import com.micropay.backend.domain.ports.out.TopUpRepository;
import com.micropay.backend.domain.ports.out.TransactionRepository;
import com.micropay.backend.domain.ports.out.WalletRepository;
import com.micropay.backend.domain.valueobjects.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Casos de uso Top-Ups (recargas).
 *
 * Flujo:
 *  1. createCheckout(user,walletId,amount,urls) → guarda TopUp PENDING_PAYMENT + devuelve checkout URL proveedor
 *  2. handleWebhook(payload,signature) → verify signature → parse evento →
 *     si SUCCEEDED: REQUIRES_NEW tx → TopUp.markPaid(txId) + Wallet.credit() + Transaction DEPOSIT_TOPUP 1 AccountMove crédito.
 *     si FAILED: TopUp.markFailed() + notification TOP_UP_FAILED (sin ledger side-effects).
 */
@Service
@Transactional(readOnly = true)
public class TopUpService {

    private static final Logger log = LoggerFactory.getLogger(TopUpService.class);

    private final TopUpRepository topUps;
    private final WalletRepository wallets;
    private final TransactionRepository transactions;
    private final PaymentProviderAdapter payments;
    private final NotificationAdapter notifications;

    public TopUpService(TopUpRepository topUps,
                        WalletRepository wallets,
                        TransactionRepository transactions,
                        PaymentProviderAdapter payments,
                        NotificationAdapter notifications) {
        this.topUps = topUps;
        this.wallets = wallets;
        this.transactions = transactions;
        this.payments = payments;
        this.notifications = notifications;
    }

    // ────────────────── 1. Crear checkout ──────────────────

    @Transactional(rollbackFor = Exception.class)
    public TopUp createCheckout(UserId userId,
                                WalletId walletId,
                                Money amount,
                                String successUrl,
                                String cancelUrl,
                                String idempotencyKey) {
        if (amount == null || !amount.isPositive()) {
            throw new BusinessRuleViolationException("TOPUP_AMOUNT_INVALID", "Monto top-up debe ser > 0");
        }
        Wallet wallet = wallets.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId.uuid()));
        if (!wallet.userId().equals(userId)) {
            throw new BusinessRuleViolationException("WALLET_NOT_OWNED_BY_USER",
                    "Wallet %s no pertenece al usuario %s".formatted(walletId, userId));
        }
        if (wallet.status() != Wallet.Status.ACTIVE) {
            throw new BusinessRuleViolationException("WALLET_NOT_ACTIVE",
                    "Wallet %s estado %s no permite recargas".formatted(walletId, wallet.status()));
        }
        if (!wallet.currency().equals(amount.currency())) {
            throw new BusinessRuleViolationException("CURRENCY_MISMATCH",
                    "Wallet currency=%s vs amount currency=%s".formatted(wallet.currency(), amount.currency()));
        }
        Currency cur = amount.currency();
        Money zeroFee = Money.zero(cur);

        PaymentProviderAdapter.CheckoutResult cr = payments.createCheckout(
                amount, walletId, successUrl, cancelUrl, idempotencyKey);

        TopUp t = TopUp.builder()
                .userId(userId)
                .walletId(walletId)
                .grossAmount(amount)
                .netAmount(amount)
                .feeAmount(zeroFee)
                .provider("STRIPE_MOCK")
                .providerRef(cr.providerRef())
                .checkoutUrl(cr.checkoutUrl())
                .status(TopUp.Status.PENDING_PAYMENT)
                .successUrl(successUrl)
                .cancelUrl(cancelUrl)
                .idempotencyKey(idempotencyKey)
                .createdBy(userId.uuid())
                .build();
        TopUp saved = topUps.save(t);
        log.info("[TOPUP] Creado checkout topUp={} wallet={} amount={} providerRef={}",
                saved.id(), walletId, amount, cr.providerRef());
        return saved;
    }

    // ────────────────── 2. Manejar webhook (público) ──────────────────

    /**
     * Consume webhook del proveedor. Devuelve:
     *  - 0 = OK procesado o idempotente (2xx HTTP)
     *  - 1 = firma inválida (401)
     *  - 2 = evento UNKNOWN (204, sin procesar)
     */
    public int handleWebhook(String rawBody, String signatureHeader) {
        if (!payments.validateWebhook(rawBody, signatureHeader)) {
            log.warn("[TOPUP] Webhook firma inválida signatureHeader={}", signatureHeader);
            return 1;
        }
        Optional<PaymentProviderAdapter.ParsedWebhook> evOpt = payments.parseWebhook(rawBody);
        if (evOpt.isEmpty() || evOpt.get().kind() == PaymentProviderAdapter.WebhookKind.UNKNOWN) {
            return 2;
        }
        PaymentProviderAdapter.ParsedWebhook ev = evOpt.get();
        TopUp topUp = topUps.findByProviderRef(ev.providerRef())
                .orElseThrow(() -> new BusinessRuleViolationException("TOPUP_NOT_FOUND",
                        "TopUp con providerRef=" + ev.providerRef() + " no encontrado"));
        return switch (ev.kind()) {
            case TOPUP_SUCCEEDED -> creditOnPaid(topUp, ev) == null ? 0 : 0;
            case TOPUP_FAILED, TOPUP_REFUNDED -> { markFailed(topUp, ev.failureReason()); yield 0; }
            default -> 2;
        };
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public TransactionId creditOnPaid(TopUp topUp, PaymentProviderAdapter.ParsedWebhook ev) {
        if (topUp.status() == TopUp.Status.PAID) {
            log.info("[TOPUP] Idempotente: ya PAID topUp={} providerRef={}", topUp.id(), topUp.providerRef());
            return topUp.transactionRef();
        }
        Wallet wallet = wallets.findById(topUp.walletId())
                .orElseThrow(() -> new WalletNotFoundException(topUp.walletId().uuid()));

        Money creditAmount = topUp.netAmount();
        Money creditAmountPositive = creditAmount;

        Money balanceAfter = wallet.credit(creditAmountPositive, "TopUp webhook PAID providerRef=" + topUp.providerRef());
        wallets.save(wallet);

        Transaction.Builder txB = Transaction.builder()
                .type(TransactionType.DEPOSIT_TOPUP)
                .walletFromId(null)   // DEPOSIT: from=null
                .walletToId(topUp.walletId())
                .grossAmount(topUp.grossAmount())
                .netAmount(topUp.netAmount())
                .feeAmount(topUp.feeAmount())
                .status(TransactionStatus.COMPLETED)
                .externalRef(topUp.providerRef())
                .createdBy(topUp.userId().uuid())
                .note("TopUp provider=" + topUp.provider() + " ref=" + topUp.providerRef());
        Transaction tx = txB.build();
        List<AccountMove> moves = new ArrayList<>(1);
        moves.add(new AccountMove(UUID.randomUUID(),
                topUp.walletId(), tx.id(),
                creditAmount, balanceAfter, Instant.now()));
        txB.accountMoves(moves);
        Transaction savedTx = transactions.save(txB.build());

        topUp.markPaid(savedTx.id());
        topUps.save(topUp);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                trySend(topUp.userId(), NotificationAdapter.Channel.PUSH,
                        "TOP_UP_SUCCESS",
                        Map.of("top_up_id", topUp.id().toString(),
                                "amount", topUp.grossAmount().toString(),
                                "wallet", topUp.walletId().toString(),
                                "tx_id", savedTx.id().toString()));
                log.info("[TOPUP] ✅ Crédito exitoso topUp={} tx={} wallet={} amount={}",
                        topUp.id(), savedTx.id(), topUp.walletId(), topUp.grossAmount());
            }
        });
        return savedTx.id();
    }

    @Transactional(rollbackFor = Exception.class)
    public void markFailed(TopUp topUp, String reason) {
        if (topUp.status() == TopUp.Status.PAID) return;
        topUp.markFailed(reason == null ? "PAYMENT_FAILED" : reason);
        topUps.save(topUp);
        trySend(topUp.userId(), NotificationAdapter.Channel.PUSH,
                "TOP_UP_FAILED",
                Map.of("top_up_id", topUp.id().toString(),
                        "provider_ref", topUp.providerRef(),
                        "reason", topUp.failureReason()));
        log.warn("[TOPUP] ❌ FAILED topUp={} reason={}", topUp.id(), topUp.failureReason());
    }

    // ────────────────── 3. Consultas (usadas por Controller) ──────────────────

    public Optional<TopUp> findById(TopUpId id) { return topUps.findById(id); }

    public Optional<TopUp> findByProviderRef(String providerRef) { return topUps.findByProviderRef(providerRef); }

    public List<TopUp> listByWallet(WalletId walletId, UserId current, int page, int pageSize, boolean isAdmin) {
        Wallet w = wallets.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId.uuid()));
        if (!isAdmin && !w.userId().equals(current)) {
            throw new BusinessRuleViolationException("WALLET_NOT_OWNED_BY_USER",
                    "Wallet %s no pertenece al usuario".formatted(walletId));
        }
        return topUps.listByWallet(walletId, page, pageSize);
    }

    public List<TopUp> listByUser(UserId userId, int page, int pageSize) {
        return topUps.listByUser(userId, page, pageSize);
    }

    // ────────────────── 4. Sandbox endpoint: aprobar mock webhook interno ──────────────────
    //    Invocado desde GET /api/v1/top-ups/mock-approve/{providerRef} en Controller.
    //    Simula que el proveedor envía un webhook succeeded vía llamado directo al service (sin HTTP loopback).

    @Transactional(rollbackFor = Exception.class)
    public TopUp sandboxApproveByProviderRef(String providerRef) {
        TopUp t = topUps.findByProviderRef(providerRef)
                .orElseThrow(() -> new BusinessRuleViolationException("TOPUP_NOT_FOUND",
                        "Sandbox approve: providerRef=" + providerRef + " no encontrado"));
        Money paid = t.netAmount();
        PaymentProviderAdapter.ParsedWebhook ev = new PaymentProviderAdapter.ParsedWebhook(
                PaymentProviderAdapter.WebhookKind.TOPUP_SUCCEEDED, providerRef, paid, null);
        // delegamos el REQUIRES_NEW vía method-2: el caller creditOnPaid anotado REQUIRES_NEW
        creditOnPaid(t, ev);
        return t;
    }

    private void trySend(UserId userId, NotificationAdapter.Channel ch, String tpl, Map<String,Object> payload) {
        try {
            notifications.send(new NotificationAdapter.NotificationRequest(userId, ch, tpl, payload));
        } catch (Exception e) {
            log.warn("No se pudo enviar notificación {} a user {}: {}", tpl, userId, e.getMessage());
        }
    }
}
