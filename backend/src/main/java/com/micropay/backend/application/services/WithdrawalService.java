package com.micropay.backend.application.services;

import com.micropay.backend.domain.entities.AccountMove;
import com.micropay.backend.domain.entities.PayoutDestination;
import com.micropay.backend.domain.entities.Transaction;
import com.micropay.backend.domain.entities.User;
import com.micropay.backend.domain.entities.Wallet;
import com.micropay.backend.domain.entities.Withdrawal;
import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.exceptions.UserNotFoundException;
import com.micropay.backend.domain.exceptions.WalletNotFoundException;
import com.micropay.backend.domain.ports.out.NotificationAdapter;
import com.micropay.backend.domain.ports.out.PayoutDestinationRepository;
import com.micropay.backend.domain.ports.out.PayoutProviderPort;
import com.micropay.backend.domain.ports.out.TransactionRepository;
import com.micropay.backend.domain.ports.out.UserRepository;
import com.micropay.backend.domain.ports.out.WalletRepository;
import com.micropay.backend.domain.ports.out.WithdrawalRepository;
import com.micropay.backend.domain.valueobjects.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Casos de uso Retiros (Withdrawals).
 *
 * Flujo:
 *  1. requestWithdrawal(user,wallet,destino,amount) → valida KYC ≥ LEVEL_1, destino verificado,
 *     wallet ACTIVE/moneda; RESERVA fondos (hold) en wallet; crea Withdrawal REQUESTED;
 *     instruye payout provider (initiatePayout) → Withdrawal PROCESSING + providerRef;
 *     registra Transaction WITHDRAW (1 AccountMove DÉBITO).
 *  2. handlePayoutUpdate(payload,signature) → verify firma → parse evento:
 *     COMPLETED → confirmReserveAsDebit (consume hold) + Withdrawal.markCompleted().
 *     FAILED/RETURNED → releaseReserve (libera hold) + Withdrawal.markFailed(reason).
 *
 * INVARIANTE: los fondos se retienen desde la solicitud; si el payout falla se liberan íntegros.
 */
@Service
@Transactional(readOnly = true)
public class WithdrawalService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalService.class);
    private static final String PROVIDER = "STRIPE_MOCK";

    private final WithdrawalRepository withdrawals;
    private final PayoutDestinationRepository destinations;
    private final WalletRepository wallets;
    private final TransactionRepository transactions;
    private final UserRepository users;
    private final PayoutProviderPort payouts;
    private final NotificationAdapter notifications;

    public WithdrawalService(WithdrawalRepository withdrawals,
                             PayoutDestinationRepository destinations,
                             WalletRepository wallets,
                             TransactionRepository transactions,
                             UserRepository users,
                             PayoutProviderPort payouts,
                             NotificationAdapter notifications) {
        this.withdrawals = withdrawals;
        this.destinations = destinations;
        this.wallets = wallets;
        this.transactions = transactions;
        this.users = users;
        this.payouts = payouts;
        this.notifications = notifications;
    }

    // ────────────────── 1. Solicitar retiro (BR 20.1/20.2/20.6) ──────────────────

    @Transactional(rollbackFor = Exception.class)
    public Withdrawal requestWithdrawal(UserId userId,
                                        WalletId walletId,
                                        PayoutDestinationId destinationId,
                                        Money amount,
                                        String idempotencyKey) {
        if (amount == null || !amount.isPositive()) {
            throw new BusinessRuleViolationException("WITHDRAWAL_AMOUNT_INVALID", "Monto retiro debe ser > 0");
        }

        User user = users.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId.uuid()));
        user.assertCanOperate("retiro");
        if (!user.kycLevel().atLeast(KycLevel.LEVEL_1)) {
            throw new BusinessRuleViolationException("WITHDRAWAL_KYC_REQUIRED",
                    "Retiro requiere KYC >= LEVEL_1, actual " + user.kycLevel());
        }

        Wallet wallet = wallets.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId.uuid()));
        if (!wallet.userId().equals(userId)) {
            throw new BusinessRuleViolationException("WALLET_NOT_OWNED_BY_USER",
                    "Wallet %s no pertenece al usuario %s".formatted(walletId, userId));
        }
        if (wallet.status() != Wallet.Status.ACTIVE) {
            throw new BusinessRuleViolationException("WALLET_NOT_ACTIVE",
                    "Wallet %s estado %s no permite retiros".formatted(walletId, wallet.status()));
        }
        if (!wallet.currency().equals(amount.currency())) {
            throw new BusinessRuleViolationException("CURRENCY_MISMATCH",
                    "Wallet currency=%s vs amount currency=%s".formatted(wallet.currency(), amount.currency()));
        }

        PayoutDestination dest = destinations.findById(destinationId)
                .orElseThrow(() -> new BusinessRuleViolationException("PAYOUT_DESTINATION_NOT_FOUND",
                        "PayoutDestination " + destinationId + " no encontrado"));
        if (!dest.userId().equals(userId)) {
            throw new BusinessRuleViolationException("PAYOUT_DESTINATION_NOT_OWNED",
                    "PayoutDestination %s no pertenece al usuario %s".formatted(destinationId, userId));
        }
        if (!dest.verified()) {
            throw new BusinessRuleViolationException("PAYOUT_DESTINATION_NOT_VERIFIED",
                    "PayoutDestination %s no está verificada".formatted(destinationId));
        }

        Currency cur = amount.currency();
        Money fee = Money.zero(cur);
        Money net = amount.subtract(fee); // sin comisión en sandbox: net = gross

        // 1. Reserva de fondos (hold) sobre la wallet
        wallet.reserve(amount, "withdraw request");
        wallets.save(wallet);

        // 2. Crear Withdrawal REQUESTED
        Map<String,String> md = new HashMap<>();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) md.put("idempotency_key", idempotencyKey);
        Withdrawal w = Withdrawal.builder()
                .userId(userId)
                .walletId(walletId)
                .payoutDestinationId(destinationId)
                .grossAmount(amount)
                .netAmount(net)
                .feeAmount(fee)
                .provider(PROVIDER)
                .status(Withdrawal.Status.REQUESTED)
                .note("Retiro a " + dest.maskedLabel())
                .metadata(md)
                .createdBy(userId.uuid())
                .build();
        w = withdrawals.save(w);

        // 3. Instruir payout provider (NO mueve fondos; sólo asigna providerRef)
        String key = (idempotencyKey == null || idempotencyKey.isBlank()) ? w.id().toString() : idempotencyKey;
        PayoutProviderPort.PayoutInitResult init = payouts.initiatePayout(net, dest.maskedLabel(), key);
        w.markProcessing(init.providerRef());
        w = withdrawals.save(w);

        // 4. Ledger: Transaction WITHDRAW + 1 AccountMove DÉBITO (from=wallet, to=null)
        Money balanceAfter = wallet.balance();
        Transaction.Builder txB = Transaction.builder()
                .type(TransactionType.WITHDRAW)
                .walletFromId(walletId)
                .walletToId(null)          // WITHDRAW: to=null
                .grossAmount(amount)
                .netAmount(net)
                .feeAmount(fee)
                .status(TransactionStatus.PROCESSING)
                .externalRef(init.providerRef())
                .createdBy(userId.uuid())
                .note("Withdrawal provider=" + PROVIDER + " ref=" + init.providerRef());
        Transaction tx = txB.build();
        List<AccountMove> moves = new ArrayList<>(1);
        moves.add(new AccountMove(UUID.randomUUID(), walletId, tx.id(),
                Money.of(amount.amount().negate(), cur), balanceAfter, Instant.now()));
        txB.accountMoves(moves);
        transactions.save(txB.build());

        log.info("[WITHDRAWAL] Solicitado id={} wallet={} amount={} providerRef={} (fondos reservados)",
                w.id(), walletId, amount, init.providerRef());
        return w;
    }

    // ────────────────── 2. Webhook / polling del payout provider (BR 20.3/20.4) ──────────────────

    /**
     * Consume webhook de payout. Devuelve:
     *  - 0 = OK procesado o idempotente (2xx)
     *  - 1 = firma inválida (401)
     *  - 2 = evento UNKNOWN (204, sin accionar)
     */
    @Transactional(rollbackFor = Exception.class)
    public int handlePayoutUpdate(String rawBody, String signatureHeader) {
        if (!payouts.validateWebhook(rawBody, signatureHeader)) {
            log.warn("[WITHDRAWAL] Webhook payout firma inválida header={}", signatureHeader);
            return 1;
        }
        Optional<PayoutProviderPort.ParsedPayoutEvent> evOpt = payouts.parseWebhook(rawBody);
        if (evOpt.isEmpty() || evOpt.get().kind() == PayoutProviderPort.PayoutEventKind.UNKNOWN) {
            return 2;
        }
        PayoutProviderPort.ParsedPayoutEvent ev = evOpt.get();
        Withdrawal w = withdrawals.findByProviderRef(ev.providerRef())
                .orElseThrow(() -> new BusinessRuleViolationException("WITHDRAWAL_NOT_FOUND",
                        "Withdrawal con providerRef=" + ev.providerRef() + " no encontrado"));
        switch (ev.kind()) {
            case PAYOUT_COMPLETED -> complete(w);
            case PAYOUT_FAILED, PAYOUT_RETURNED -> failAndRelease(w, ev.failureReason());
            default -> { return 2; }
        }
        return 0;
    }

    /** COMPLETED: consume el hold (confirmReserveAsDebit) y cierra el retiro. Idempotente. */
    @Transactional(rollbackFor = Exception.class)
    public Withdrawal complete(Withdrawal w) {
        if (w.status() == Withdrawal.Status.COMPLETED) {
            log.info("[WITHDRAWAL] Idempotente: ya COMPLETED id={}", w.id());
            return w;
        }
        Wallet wallet = wallets.findById(w.walletId())
                .orElseThrow(() -> new WalletNotFoundException(w.walletId().uuid()));
        wallet.confirmReserveAsDebit(w.grossAmount(), "withdraw completed");
        wallets.save(wallet);

        w.markCompleted();
        Withdrawal saved = withdrawals.save(w);

        registerNotification(saved.userId(), "WITHDRAWAL_COMPLETED",
                Map.of("withdrawal_id", saved.id().toString(),
                        "amount", saved.grossAmount().toString(),
                        "wallet", saved.walletId().toString()));
        log.info("[WITHDRAWAL] ✅ COMPLETED id={} wallet={} amount={}",
                saved.id(), saved.walletId(), saved.grossAmount());
        return saved;
    }

    /** FAILED/RETURNED: libera el hold y marca el retiro fallido. Idempotente. */
    @Transactional(rollbackFor = Exception.class)
    public Withdrawal failAndRelease(Withdrawal w, String reason) {
        if (w.status() == Withdrawal.Status.FAILED) {
            log.info("[WITHDRAWAL] Idempotente: ya FAILED id={}", w.id());
            return w;
        }
        if (!w.isTerminal()) {
            Wallet wallet = wallets.findById(w.walletId())
                    .orElseThrow(() -> new WalletNotFoundException(w.walletId().uuid()));
            wallet.releaseReserve(w.grossAmount(), "withdraw failed");
            wallets.save(wallet);
        }
        w.markFailed(reason == null ? "PAYOUT_FAILED" : reason);
        Withdrawal saved = withdrawals.save(w);

        registerNotification(saved.userId(), "WITHDRAWAL_FAILED",
                Map.of("withdrawal_id", saved.id().toString(),
                        "amount", saved.grossAmount().toString(),
                        "reason", saved.failureReason() == null ? "" : saved.failureReason()));
        log.warn("[WITHDRAWAL] ❌ FAILED id={} reason={} (fondos liberados)", saved.id(), saved.failureReason());
        return saved;
    }

    // ────────────────── 3. Sandbox: resolver payout sin loopback HTTP ──────────────────

    @Transactional(rollbackFor = Exception.class)
    public Withdrawal sandboxCompleteByProviderRef(String providerRef) {
        Withdrawal w = withdrawals.findByProviderRef(providerRef)
                .orElseThrow(() -> new BusinessRuleViolationException("WITHDRAWAL_NOT_FOUND",
                        "Sandbox complete: providerRef=" + providerRef + " no encontrado"));
        return complete(w);
    }

    @Transactional(rollbackFor = Exception.class)
    public Withdrawal sandboxFailByProviderRef(String providerRef, String reason) {
        Withdrawal w = withdrawals.findByProviderRef(providerRef)
                .orElseThrow(() -> new BusinessRuleViolationException("WITHDRAWAL_NOT_FOUND",
                        "Sandbox fail: providerRef=" + providerRef + " no encontrado"));
        return failAndRelease(w, reason);
    }

    // ────────────────── 4. Consultas ──────────────────

    public Optional<Withdrawal> findById(WithdrawalId id) { return withdrawals.findById(id); }

    public Optional<Withdrawal> findByProviderRef(String providerRef) { return withdrawals.findByProviderRef(providerRef); }

    public List<Withdrawal> listByWallet(WalletId walletId, UserId current, int page, int pageSize, boolean isAdmin) {
        Wallet w = wallets.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId.uuid()));
        if (!isAdmin && !w.userId().equals(current)) {
            throw new BusinessRuleViolationException("WALLET_NOT_OWNED_BY_USER",
                    "Wallet %s no pertenece al usuario".formatted(walletId));
        }
        return withdrawals.listByWallet(walletId, page, pageSize);
    }

    public List<Withdrawal> listByUser(UserId userId, int page, int pageSize) {
        return withdrawals.listByUser(userId, page, pageSize);
    }

    // ────────────────── Helper notificación after-commit ──────────────────

    private void registerNotification(UserId userId, String template, Map<String,Object> payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { trySend(userId, template, payload); }
            });
        } else {
            trySend(userId, template, payload);
        }
    }

    private void trySend(UserId userId, String template, Map<String,Object> payload) {
        try {
            notifications.send(new NotificationAdapter.NotificationRequest(
                    userId, NotificationAdapter.Channel.PUSH, template, payload));
        } catch (Exception e) {
            log.warn("No se pudo enviar notificación {} a user {}: {}", template, userId, e.getMessage());
        }
    }
}
