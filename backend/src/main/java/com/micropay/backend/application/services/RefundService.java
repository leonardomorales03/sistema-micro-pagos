package com.micropay.backend.application.services;

import com.micropay.backend.domain.entities.AccountMove;
import com.micropay.backend.domain.entities.Refund;
import com.micropay.backend.domain.entities.Transaction;
import com.micropay.backend.domain.entities.User;
import com.micropay.backend.domain.entities.Wallet;
import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.exceptions.ConcurrencyConflictException;
import com.micropay.backend.domain.exceptions.TransactionNotFoundException;
import com.micropay.backend.domain.exceptions.UserNotFoundException;
import com.micropay.backend.domain.exceptions.WalletNotFoundException;
import com.micropay.backend.domain.ports.out.NotificationAdapter;
import com.micropay.backend.domain.ports.out.RefundRepository;
import com.micropay.backend.domain.ports.out.TransactionRepository;
import com.micropay.backend.domain.ports.out.UserRepository;
import com.micropay.backend.domain.ports.out.WalletLockAdapter;
import com.micropay.backend.domain.ports.out.WalletRepository;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.FeeContext;
import com.micropay.backend.domain.valueobjects.FeeOperation;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.RefundType;
import com.micropay.backend.domain.valueobjects.TransactionId;
import com.micropay.backend.domain.valueobjects.TransactionStatus;
import com.micropay.backend.domain.valueobjects.TransactionType;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;
import com.micropay.backend.infrastructure.config.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Casos de uso Reembolsos (Requirement 23) + integración con el FeeEngine.
 *
 * Flujo createRefund(refundingUser, sourceTxId, amount, reason):
 *  1. Valida: tipo de origen reembolsable, estado COMPLETED, moneda coincide,
 *     monto ≤ refundable restante (acumulado, Property 26), party = receptor original o admin.
 *  2. Ejecuta un REVERSE TRANSFER atómico: débito wallet receptor original + crédito wallet pagador
 *     original (si existe), con locks distribuidos ordenados + Spring Retry + doble entrada.
 *  3. Persiste el Refund (vínculo bidireccional refund_tx_id ↔ source_tx_id) distinguiendo FULL/PARTIAL.
 *  4. Si el acumulado alcanza el monto original → marca la transacción origen REFUNDED.
 *  5. Registra comisiones (0 por defecto en reembolsos) y notifica post-commit.
 *
 * INVARIANTE (Requirement 23.6 / Property 26): si el receptor no tiene saldo disponible,
 * el débito falla y TODA la operación se revierte sin efectos secundarios (HTTP 422).
 */
@Service
@Transactional(readOnly = true)
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);
    private static final String DEFAULT_COUNTRY = "CO";
    private static final Set<TransactionType> REFUNDABLE_TYPES = Set.of(
            TransactionType.TRANSFER_P2P,
            TransactionType.DEPOSIT_TOPUP,
            TransactionType.PAYMENT_REQUEST,
            TransactionType.SUBSCRIPTION_CHARGE);

    private final RefundRepository refunds;
    private final TransactionRepository transactions;
    private final WalletRepository wallets;
    private final UserRepository users;
    private final WalletLockAdapter walletLock;
    private final FeeService feeService;
    private final NotificationAdapter notifications;
    private final org.springframework.retry.support.RetryTemplate refundRetryTemplate;
    private final TransactionTemplate refundTransaction;

    public RefundService(RefundRepository refunds,
                         TransactionRepository transactions,
                         WalletRepository wallets,
                         UserRepository users,
                         WalletLockAdapter walletLock,
                         FeeService feeService,
                         NotificationAdapter notifications,
                         @Qualifier("transferRetryTemplate")
                         org.springframework.retry.support.RetryTemplate refundRetryTemplate,
                         PlatformTransactionManager transactionManager) {
        this.refunds = refunds;
        this.transactions = transactions;
        this.wallets = wallets;
        this.users = users;
        this.walletLock = walletLock;
        this.feeService = feeService;
        this.notifications = notifications;
        this.refundRetryTemplate = refundRetryTemplate;
        this.refundTransaction = new TransactionTemplate(transactionManager);
        this.refundTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Resultado: el reembolso registrado + la transacción de reverse transfer generada. */
    public record RefundResult(Refund refund, Transaction refundTransaction) {}

    // ────────────────── Crear reembolso ──────────────────

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RefundResult createRefund(UserId refundingUserId,
                                     TransactionId sourceTxId,
                                     Money amount,
                                     String reason) {
        if (refundingUserId == null) throw new IllegalArgumentException("refundingUserId");
        if (sourceTxId == null) throw new IllegalArgumentException("sourceTxId");
        if (amount == null || !amount.isPositive()) {
            throw new BusinessRuleViolationException("REFUND_AMOUNT_INVALID", "Monto refund debe ser > 0");
        }
        try {
            return refundRetryTemplate.execute(
                    ctx -> {
                        try {
                            return doCreateRefund(1 + ctx.getRetryCount(), refundingUserId, sourceTxId, amount, reason);
                        } catch (RuntimeException ex) {
                            throw classifyForRetry(ex);
                        }
                    },
                    ctx -> {
                        Throwable cause = ctx.getLastThrowable();
                        if (ctx.getRetryCount() < RetryConfig.MAX_ATTEMPTS
                                && cause instanceof RuntimeException runtime) {
                            throw runtime;
                        }
                        int attempt = ctx.getRetryCount();
                        log.atError()
                                .addKeyValue("operation", "createRefund")
                                .addKeyValue("attempt", attempt)
                                .addKeyValue("source_tx", String.valueOf(sourceTxId))
                                .addKeyValue("last_cause", cause == null ? null : cause.getClass().getSimpleName())
                                .log("MAX_RETRIES_EXCEEDED createRefund attempts={}", attempt);
                        throw new ConcurrencyConflictException(
                                "MAX_CONCURRENCY_RETRIES_EXCEEDED",
                                ("Reembolso no procesado tras %d reintentos por conflicto de concurrencia. " +
                                 "Reintente en 2-5s.").formatted(attempt),
                                attempt,
                                "createRefund",
                                cause);
                    });
        } catch (ConcurrencyConflictException cce) {
            throw cce;
        } catch (RuntimeException re) {
            Throwable unwrapped = unwrapRetryWrapper(re);
            if (unwrapped instanceof RuntimeException runtimeUnwrapped) {
                throw runtimeUnwrapped;
            }
            throw re;
        }
    }

    private RefundResult doCreateRefund(int attempt,
                                        UserId refundingUserId,
                                        TransactionId sourceTxId,
                                        Money amount,
                                        String reason) {
        Transaction source = transactions.findById(sourceTxId)
                .orElseThrow(() -> new TransactionNotFoundException(sourceTxId.uuid()));

        WalletId debitWalletId = source.walletToId();      // receptor original (a debitar)
        WalletId creditWalletId = source.walletFromId();   // pagador original (a acreditar, puede ser null)
        if (debitWalletId == null) {
            throw new BusinessRuleViolationException("REFUND_NO_RECIPIENT_WALLET",
                    "La transacción %s no tiene wallet receptora reembolsable".formatted(sourceTxId));
        }

        // Locks distribuidos ordenados por UUID (previene deadlock)
        List<WalletId> lockIds = new ArrayList<>(2);
        lockIds.add(debitWalletId);
        if (creditWalletId != null && !creditWalletId.equals(debitWalletId)) {
            lockIds.add(creditWalletId);
        }
        lockIds.sort(Comparator.comparing(WalletId::uuid));

        List<WalletId> acquired = new ArrayList<>(lockIds.size());
        try {
            for (WalletId id : lockIds) {
                acquireLockOrFail(id, attempt);
                acquired.add(id);
            }
            return refundTransaction.execute(status ->
                    refundWithinTx(attempt, refundingUserId, sourceTxId, amount, reason));
        } finally {
            for (int i = acquired.size() - 1; i >= 0; i--) {
                walletLock.unlock(acquired.get(i));
            }
        }
    }

    private RefundResult refundWithinTx(int attempt,
                                        UserId refundingUserId,
                                        TransactionId sourceTxId,
                                        Money amount,
                                        String reason) {
        Transaction source = transactions.findById(sourceTxId)
                .orElseThrow(() -> new TransactionNotFoundException(sourceTxId.uuid()));

        // ── Validaciones dominio (Requirement 23.1, 23.2, 23.4) ──
        if (!REFUNDABLE_TYPES.contains(source.type())) {
            throw new BusinessRuleViolationException("REFUND_SOURCE_NOT_REFUNDABLE",
                    "Tipo %s no es reembolsable".formatted(source.type()));
        }
        if (source.status() != TransactionStatus.COMPLETED) {
            throw new BusinessRuleViolationException("REFUND_SOURCE_NOT_COMPLETED",
                    "Sólo transacciones COMPLETED son reembolsables (estado %s)".formatted(source.status()));
        }
        Currency cur = source.grossAmount().currency();
        if (!amount.currency().equals(cur)) {
            throw new BusinessRuleViolationException("CURRENCY_MISMATCH",
                    "Moneda del refund %s no coincide con la original %s".formatted(amount.currency(), cur));
        }
        Money alreadyRefunded = refunds.sumRefundedFor(sourceTxId, cur);
        Money remaining = source.grossAmount().subtract(alreadyRefunded);
        if (!remaining.isPositive()) {
            throw new BusinessRuleViolationException("REFUND_ALREADY_FULLY_REFUNDED",
                    "La transacción %s ya fue reembolsada en su totalidad".formatted(sourceTxId));
        }
        if (amount.isGreaterThan(remaining)) {
            throw new BusinessRuleViolationException("REFUND_EXCEEDS_REMAINING",
                    "Refund %s excede el reembolsable restante %s".formatted(amount, remaining));
        }

        User user = users.findById(refundingUserId)
                .orElseThrow(() -> new UserNotFoundException(refundingUserId.uuid()));
        boolean admin = user.hasRole("ROLE_ADMIN");
        Wallet debitWallet = wallets.findById(source.walletToId())
                .orElseThrow(() -> new WalletNotFoundException(source.walletToId().uuid()));
        if (!admin && !debitWallet.userId().equals(refundingUserId)) {
            throw new BusinessRuleViolationException("REFUND_NOT_AUTHORIZED",
                    "Sólo el receptor original o un admin pueden reembolsar la transacción %s".formatted(sourceTxId));
        }

        // ── Reverse transfer atómico (doble entrada) ──
        Money balanceAfterDebit = debitWallet.debit(amount, "refund");
        wallets.save(debitWallet);

        Wallet creditWallet = source.walletFromId() == null ? null
                : wallets.findById(source.walletFromId())
                        .orElseThrow(() -> new WalletNotFoundException(source.walletFromId().uuid()));
        Money balanceAfterCredit = null;
        if (creditWallet != null) {
            balanceAfterCredit = creditWallet.credit(amount, "refund");
            wallets.save(creditWallet);
        }

        String note = reason == null || reason.isBlank()
                ? "Refund source=" + sourceTxId
                : "Refund source=" + sourceTxId + " reason=" + reason;
        Transaction.Builder tb = Transaction.builder()
                .type(TransactionType.REFUND)
                .walletFromId(debitWallet.id())
                .walletToId(creditWallet != null ? creditWallet.id() : null)
                .grossAmount(amount)
                .netAmount(amount)
                .feeAmount(Money.zero(cur))
                .status(TransactionStatus.COMPLETED)
                .createdBy(refundingUserId.uuid())
                .note(note);
        Transaction refundTx = tb.build();
        List<AccountMove> moves = new ArrayList<>(2);
        moves.add(new AccountMove(UUID.randomUUID(), debitWallet.id(), refundTx.id(),
                Money.of(amount.amount().negate(), cur), balanceAfterDebit, Instant.now()));
        if (creditWallet != null) {
            moves.add(new AccountMove(UUID.randomUUID(), creditWallet.id(), refundTx.id(),
                    amount, balanceAfterCredit, Instant.now()));
        }
        tb.accountMoves(moves);
        Transaction savedTx = transactions.save(tb.build());

        // ── Clasificación FULL/PARTIAL y registro (Requirement 23.5) ──
        Money newTotal = alreadyRefunded.add(amount);
        RefundType type = newTotal.isGreaterThanOrEqual(source.grossAmount())
                ? RefundType.FULL : RefundType.PARTIAL;

        Refund refund = Refund.builder()
                .sourceTransactionId(sourceTxId)
                .refundingUserId(refundingUserId)
                .refundedAmount(amount)
                .type(type)
                .reason(reason)
                .refundTxId(savedTx.id())
                .build();
        Refund savedRefund = refunds.save(refund);

        // Comisión de reembolso (0 por defecto — el fee original no se devuelve)
        feeService.applyAndRecord(savedTx,
                new FeeContext(FeeOperation.REFUND, amount, user.kycLevel(), DEFAULT_COUNTRY));

        if (type == RefundType.FULL) {
            transactions.updateStatus(sourceTxId, TransactionStatus.REFUNDED);
        }

        // Notificaciones post-commit
        UserId payerUserId = creditWallet != null ? creditWallet.userId() : null;
        registerNotification(() -> {
            trySend(refundingUserId, "REFUND_ISSUED", Map.of(
                    "refund_id", savedRefund.id().toString(),
                    "source_tx_id", sourceTxId.toString(),
                    "refund_tx_id", savedTx.id().toString(),
                    "amount", amount.toString(),
                    "type", type.name()));
            if (payerUserId != null) {
                trySend(payerUserId, "REFUND_RECEIVED", Map.of(
                        "refund_id", savedRefund.id().toString(),
                        "source_tx_id", sourceTxId.toString(),
                        "amount", amount.toString()));
            }
            log.atInfo()
                    .addKeyValue("operation", "createRefund")
                    .addKeyValue("attempt", attempt)
                    .addKeyValue("refund_id", String.valueOf(savedRefund.id()))
                    .addKeyValue("refund_tx_id", String.valueOf(savedTx.id()))
                    .addKeyValue("source_tx_id", String.valueOf(sourceTxId))
                    .addKeyValue("type", type.name())
                    .log("✅ Refund registrado refund={} tx={} source={} tipo={}", savedRefund.id(), savedTx.id(), sourceTxId, type);
        });

        return new RefundResult(savedRefund, savedTx);
    }

    // ────────────────── Consultas ──────────────────

    public List<Refund> listBySourceTransaction(TransactionId sourceTxId) {
        return refunds.listBySourceTransaction(sourceTxId);
    }

    public Optional<Refund> findById(com.micropay.backend.domain.valueobjects.RefundId id) {
        return refunds.findById(id);
    }

    // ────────────────── Helpers concurrencia ──────────────────

    private RuntimeException classifyForRetry(RuntimeException ex) {
        Throwable unwrapped = unwrap(ex, 4);
        if (unwrapped instanceof BusinessRuleViolationException bre
                && bre.getCode() != null
                && bre.getCode().startsWith("BUSINESS_RULE_VIOLATION_WALLET_LOCK")) {
            return new RetryConfig.TransientConcurrencyException(
                    "Transient wallet lock conflict: " + bre.getCode(), unwrapped);
        }
        if (unwrapped instanceof DataAccessException dae
                && Objects.requireNonNullElse(dae.getMessage(), "").toLowerCase().contains("deadlock")) {
            return new RetryConfig.TransientConcurrencyException(
                    "Transient deadlock detected in data access", unwrapped);
        }
        return ex;
    }

    private static Throwable unwrapRetryWrapper(Throwable t) {
        Throwable cur = t;
        for (int i = 0; i < 3 && cur != null; i++) {
            if (cur instanceof RetryConfig.TransientConcurrencyException && cur.getCause() != null) {
                cur = cur.getCause();
            } else {
                break;
            }
        }
        return cur == null ? t : cur;
    }

    private static Throwable unwrap(Throwable t, int maxDepth) {
        Throwable cur = t;
        for (int i = 0; i < maxDepth && cur != null; i++) {
            if (cur.getCause() != null && cur != cur.getCause()
                    && (cur.getClass().getName().startsWith("org.springframework.")
                        || cur instanceof RetryConfig.TransientConcurrencyException)) {
                cur = cur.getCause();
            } else {
                break;
            }
        }
        return cur == null ? t : cur;
    }

    private void acquireLockOrFail(WalletId id, int attempt) {
        if (!walletLock.tryLock(id)) {
            log.atWarn()
                    .addKeyValue("operation", "createRefund")
                    .addKeyValue("attempt", attempt)
                    .addKeyValue("wallet", String.valueOf(id))
                    .log("WALLET_LOCK_CONFLICT: no pudo adquirir lock para wallet {}", id);
            throw new BusinessRuleViolationException("WALLET_LOCK_CONFLICT",
                    "createRefund intento %d: no pudo adquirir lock distribuido para wallet %s. Reintente en %ds."
                            .formatted(attempt, id, WalletLockAdapter.TTL_SECONDS));
        }
    }

    private void registerNotification(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { action.run(); }
            });
        } else {
            action.run();
        }
    }

    private void trySend(UserId userId, String template, Map<String, Object> payload) {
        try {
            notifications.send(new NotificationAdapter.NotificationRequest(
                    userId, NotificationAdapter.Channel.PUSH, template, payload));
        } catch (Exception e) {
            log.warn("No se pudo enviar notificación {} a user {}: {}", template, userId, e.getMessage());
        }
    }
}
