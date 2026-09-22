package com.micropay.backend.application.services;

import com.micropay.backend.domain.entities.AccountMove;
import com.micropay.backend.domain.entities.Transaction;
import com.micropay.backend.domain.entities.Wallet;
import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.exceptions.ConcurrencyConflictException;
import com.micropay.backend.domain.exceptions.TransactionNotFoundException;
import com.micropay.backend.domain.exceptions.WalletNotFoundException;
import com.micropay.backend.domain.ports.in.TransactionUseCases;
import com.micropay.backend.domain.ports.out.*;
import com.micropay.backend.domain.valueobjects.*;
import com.micropay.backend.infrastructure.config.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Casos de uso core: Transferencia P2P, crear wallet, listar historial.
 *
 * Política ACID + Concurrencia (T9):
 *   • REQUIRES_NEW en transferP2P (rollback unitario transaccional)
 *   • Orden determinista bloqueos wallet por UUID lexicográfico → 0 deadlock A↔B
 *   • @Version optimistic locking sobre Wallet + Redis SETNX lock distribuido TTL 10s
 *   • Spring Retry: 3 intentos, backoff exponencial 100→200→400ms.
 *   • Clasificador de excepciones: retry solo transient concurrencia/deadlock/WALLET_LOCK.
 *   • Después de 3 fallos: lanza ConcurrencyConflictException 409 + Retry-After.
 *   • Auditoría: structured log attempt, from, to, amount por intento.
 *   • Double Entry Ledger: cada TRANSFER_P2P genera 2 AccountMove.
 */
@Service
@Transactional(readOnly = true)
public class TransactionService implements TransactionUseCases {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final WalletRepository wallets;
    private final TransactionRepository transactions;
    private final WalletLockAdapter walletLock;
    private final RiskCheckAdapter riskEngine;
    private final NotificationAdapter notifications;
    private final UserRepository users;
    private final org.springframework.retry.support.RetryTemplate transferRetryTemplate;
    private final TransactionTemplate transferTransaction;

    public TransactionService(WalletRepository wallets,
                              TransactionRepository transactions,
                              WalletLockAdapter walletLock,
                              RiskCheckAdapter riskEngine,
                              NotificationAdapter notifications,
                              UserRepository users,
                              @Qualifier("transferRetryTemplate")
                              org.springframework.retry.support.RetryTemplate transferRetryTemplate,
                              PlatformTransactionManager transactionManager) {
        this.wallets = wallets;
        this.transactions = transactions;
        this.walletLock = walletLock;
        this.riskEngine = riskEngine;
        this.notifications = notifications;
        this.users = users;
        this.transferRetryTemplate = transferRetryTemplate;
        this.transferTransaction = new TransactionTemplate(transactionManager);
        this.transferTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Transaction transferP2P(WalletId fromWalletId,
                                   WalletId toWalletId,
                                   Money amount,
                                   UserId initiatedBy,
                                   String note) {
        try {
            return transferRetryTemplate.execute(
                    ctx -> {
                        try {
                            return doTransferP2P(1 + ctx.getRetryCount(), fromWalletId, toWalletId, amount, initiatedBy, note);
                        } catch (RuntimeException ex) {
                            throw classifyForRetry(ex);
                        }
                    },
                    ctx -> {
                        Throwable cause = ctx.getLastThrowable();
                        // Recovery also runs for non-retryable business failures.
                        if (ctx.getRetryCount() < RetryConfig.MAX_ATTEMPTS
                                && cause instanceof RuntimeException runtime) {
                            throw runtime;
                        }
                        int attempt = ctx.getRetryCount();
                        log.atError()
                                .addKeyValue("operation", "transferP2P")
                                .addKeyValue("attempt", attempt)
                                .addKeyValue("from_wallet", String.valueOf(fromWalletId))
                                .addKeyValue("to_wallet", String.valueOf(toWalletId))
                                .addKeyValue("amount", amount == null ? null : amount.amount().toPlainString())
                                .addKeyValue("initiated_by", String.valueOf(initiatedBy))
                                .addKeyValue("last_cause", cause == null ? null : cause.getClass().getSimpleName() + ": " + cause.getMessage())
                                .log("MAX_RETRIES_EXCEEDED transferP2P attempts={}", attempt);
                        throw new ConcurrencyConflictException(
                                "MAX_CONCURRENCY_RETRIES_EXCEEDED",
                                ("Transferencia no procesada después de %d reintentos por conflicto de concurrencia " +
                                 "(lock distribuido, optimistic locking o deadlock DB). Reintente en 2-5s.")
                                        .formatted(attempt),
                                attempt,
                                "transferP2P",
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

    // ── Classifier: excepciones custom transient que SimpleRetryPolicy no puede detectar ──

    private RuntimeException classifyForRetry(RuntimeException ex) {
        Throwable unwrapped = unwrap(ex, 4);
        // 1. BusinessRuleViolation con código WALLET_LOCK* (Redis SETNX fallo temporal)
        if (unwrapped instanceof BusinessRuleViolationException bre
                && bre.getCode() != null
                && bre.getCode().startsWith("BUSINESS_RULE_VIOLATION_WALLET_LOCK")) {
            return new RetryConfig.TransientConcurrencyException(
                    "Transient wallet lock conflict: " + bre.getCode(), unwrapped);
        }
        // 2. DataAccessException con mensaje "deadlock" (algunos wrappers no se llaman DeadlockLoser*)
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

    /**
     * Unidad transaccional real. Si se produce un excepción transitoria (deadlock, optimistic lock, lock wait),
     * el RetryTemplate (en el caller) ejecutará de nuevo este método CON UNA NUEVA TRANSACCIÓN DB.
     * De esta forma el rollback afecta SOLO al intento fallido, y al reintentar tenemos nueva conexión limpia.
     */
    private Transaction doTransferP2P(int attempt,
                                     WalletId fromWalletId,
                                     WalletId toWalletId,
                                     Money amount,
                                     UserId initiatedBy,
                                     String note) {
        if (fromWalletId == null) throw new IllegalArgumentException("fromWalletId");
        if (toWalletId == null) throw new IllegalArgumentException("toWalletId");
        if (fromWalletId.equals(toWalletId)) {
            throw new BusinessRuleViolationException("SAME_WALLET_TRANSFER",
                    "No se puede transferir a la misma wallet");
        }
        if (!amount.isPositive()) {
            throw new BusinessRuleViolationException("AMOUNT_NOT_POSITIVE",
                    "Monto debe ser > 0");
        }
        log.atInfo()
                .addKeyValue("operation", "transferP2P")
                .addKeyValue("attempt", attempt)
                .addKeyValue("from_wallet", String.valueOf(fromWalletId))
                .addKeyValue("to_wallet", String.valueOf(toWalletId))
                .addKeyValue("amount", amount.amount().toPlainString() + " " + amount.currency())
                .addKeyValue("initiated_by", String.valueOf(initiatedBy))
                .log("TransferP2P intento #{} from={} to={}", attempt, fromWalletId, toWalletId);

        // 1. Bloqueo distribuido wallets (2 lock — orden por UUID para prevenir deadlock)
        WalletId first, second;
        if (fromWalletId.uuid().compareTo(toWalletId.uuid()) < 0) {
            first = fromWalletId; second = toWalletId;
        } else {
            first = toWalletId; second = fromWalletId;
        }
        acquireLockOrFail(first, "transferP2P", attempt);
        boolean secondAcquired = false;
        try {
            acquireLockOrFail(second, "transferP2P", attempt);
            secondAcquired = true;
            // A local method call bypasses Spring's transactional proxy.
            // Keep both locks until this transaction has committed or rolled back.
            return transferTransaction.execute(status -> {
                Wallet from = wallets.findById(fromWalletId)
                        .orElseThrow(() -> new WalletNotFoundException(fromWalletId.uuid()));
                Wallet to = wallets.findById(toWalletId)
                        .orElseThrow(() -> new WalletNotFoundException(toWalletId.uuid()));

                if (!from.userId().equals(initiatedBy)) {
                    throw new BusinessRuleViolationException("WALLET_NOT_OWNED_BY_USER",
                            "Wallet %s no pertenece al usuario %s".formatted(fromWalletId, initiatedBy));
                }

                // Monedas coinciden
                if (!from.currency().equals(to.currency()) || !from.currency().equals(amount.currency())) {
                    throw new BusinessRuleViolationException("CURRENCY_MISMATCH",
                            "Transferencia requiere monedas iguales: from=%s, to=%s, amount=%s"
                                    .formatted(from.currency(), to.currency(), amount.currency()));
                }

                // 2. RiskEngine velocity counters (Redis)
                RiskCheckAdapter.RiskResult rr = riskEngine.checkTransfer(
                        initiatedBy, fromWalletId, toWalletId, amount, "localhost");
                if (!rr.allowed()) {
                    throw new BusinessRuleViolationException(
                            rr.blockedRule() != null ? rr.blockedRule() : "RISK_BLOCKED",
                            rr.detail());
                }

                // 3. Debito from + crédito to — aplica 7 reglas negocio en domain Wallet methods
                Money zeroFee = Money.zero(amount.currency());
                Money balanceAfterDebit = from.debit(amount, "transferP2P");
                Money balanceAfterCredit = to.credit(amount, "transferP2P");

                // 4. Salvar wallets actualizados → optimist lock @Version incrementa o falla si colisión
                Wallet savedFrom = wallets.save(from);
                Wallet savedTo = wallets.save(to);

                Transaction.Builder txBuilder = Transaction.builder()
                        .type(TransactionType.TRANSFER_P2P)
                        .walletFromId(fromWalletId)
                        .walletToId(toWalletId)
                        .grossAmount(amount)
                        .netAmount(amount)
                        .feeAmount(zeroFee)
                        .status(TransactionStatus.COMPLETED)
                        .createdBy(initiatedBy.uuid())
                        .note(note);

                if (note != null) txBuilder.note(note);
                Transaction tx = txBuilder.build();

                // Ledger Double Entry (2 AccountMoves)
                List<AccountMove> moves = new ArrayList<>(2);
                moves.add(new AccountMove(UUID.randomUUID(),
                        fromWalletId, tx.id(),
                        Money.of(amount.amount().negate(), amount.currency()),
                        balanceAfterDebit, Instant.now()));
                moves.add(new AccountMove(UUID.randomUUID(),
                        toWalletId, tx.id(),
                        amount, balanceAfterCredit, Instant.now()));
                txBuilder.accountMoves(moves);
                tx = txBuilder.build();

                Transaction savedTx = transactions.save(tx);

                // Publish success only after the database commit has succeeded.
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        trySendNotif(from.userId(), NotificationAdapter.Channel.PUSH,
                                "TRANSFER_SENT",
                                java.util.Map.of(
                                        "tx_id", savedTx.id().toString(),
                                        "amount", amount.toString(),
                                        "to_wallet", toWalletId.toString()
                                ));
                        trySendNotif(to.userId(), NotificationAdapter.Channel.PUSH,
                                "TRANSFER_RECEIVED",
                                java.util.Map.of(
                                        "tx_id", savedTx.id().toString(),
                                        "amount", amount.toString(),
                                        "from_wallet", fromWalletId.toString()
                                ));

                        log.atInfo()
                                .addKeyValue("operation", "transferP2P")
                                .addKeyValue("attempt", attempt)
                                .addKeyValue("tx_id", String.valueOf(savedTx.id()))
                                .addKeyValue("tx_short_code", String.valueOf(savedTx.shortCode()))
                                .addKeyValue("from_wallet_version", savedFrom.version())
                                .addKeyValue("to_wallet_version", savedTo.version())
                                .log("✅ TransferP2P COMPLETED tx={} from={} to={} amount={}",
                                        savedTx.id(), fromWalletId, toWalletId, amount);

                    }
                });
                return savedTx;
            });
        } finally {
            if (secondAcquired) {
                walletLock.unlock(second);
            }
            walletLock.unlock(first);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Wallet createWallet(UserId userId, Currency currency, UserId createdBy) {
        Optional<Wallet> existing = wallets.findByUserAndCurrency(userId, currency);
        if (existing.isPresent()) {
            throw new BusinessRuleViolationException("WALLET_ALREADY_EXISTS",
                    "Usuario %s ya tiene wallet en %s".formatted(userId, currency));
        }
        Wallet wallet = Wallet.createFor(userId, currency, createdBy == null ? null : createdBy.uuid());
        return wallets.save(wallet);
    }

    @Override
    public List<Transaction> listTransactions(WalletId walletId, int page, int pageSize) {
        return transactions.listByWallet(walletId, Math.max(0, page), Math.min(100, Math.max(1, pageSize)));
    }

    @Override
    public Optional<Transaction> findById(TransactionId id) {
        return transactions.findById(id);
    }

    @Override
    public Optional<Transaction> findByShortCode(ShortCode shortCode) {
        return transactions.findByShortCode(shortCode);
    }

    // ───────────── private helpers ─────────────

    private void acquireLockOrFail(WalletId id, String op, int attempt) {
        if (!walletLock.tryLock(id)) {
            log.atWarn()
                    .addKeyValue("operation", op)
                    .addKeyValue("attempt", attempt)
                    .addKeyValue("wallet", String.valueOf(id))
                    .log("WALLET_LOCK_CONFLICT: no pudo adquirir SETNX lock distribuido para wallet {}", id);
            throw new BusinessRuleViolationException("WALLET_LOCK_CONFLICT",
                    "%s intento %d: no pudo adquirir lock distribuido para wallet %s. Reintente en %ds."
                            .formatted(op, attempt, id, WalletLockAdapter.TTL_SECONDS));
        }
    }

    private void trySendNotif(UserId userId, NotificationAdapter.Channel ch, String tpl, java.util.Map<String,Object> payload) {
        try {
            notifications.send(new NotificationAdapter.NotificationRequest(userId, ch, tpl, payload));
        } catch (Exception e) {
            log.warn("No se pudo enviar notificación {} a user {}: {}", tpl, userId, e.getMessage());
        }
    }
}
