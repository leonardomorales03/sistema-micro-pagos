package com.micropay.backend.application.services;

import com.micropay.backend.domain.entities.AccountMove;
import com.micropay.backend.domain.entities.Transaction;
import com.micropay.backend.domain.entities.Wallet;
import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.exceptions.TransactionNotFoundException;
import com.micropay.backend.domain.exceptions.WalletNotFoundException;
import com.micropay.backend.domain.ports.in.TransactionUseCases;
import com.micropay.backend.domain.ports.out.*;
import com.micropay.backend.domain.valueobjects.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Casos de uso core: Transferencia P2P, crear wallet, listar historial.
 * ACID: REQUIRES_NEW en operaciones críticas para rollback independiente.
 * Double Entry Ledger: cada TRANSFER_P2P genera 2 AccountMove.
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

    public TransactionService(WalletRepository wallets,
                              TransactionRepository transactions,
                              WalletLockAdapter walletLock,
                              RiskCheckAdapter riskEngine,
                              NotificationAdapter notifications,
                              UserRepository users) {
        this.wallets = wallets;
        this.transactions = transactions;
        this.walletLock = walletLock;
        this.riskEngine = riskEngine;
        this.notifications = notifications;
        this.users = users;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Transaction transferP2P(WalletId fromWalletId,
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

        // 1. Bloqueo distribuido wallets (2 lock — orden por UUID para prevenir deadlock)
        WalletId first, second;
        if (fromWalletId.uuid().compareTo(toWalletId.uuid()) < 0) {
            first = fromWalletId; second = toWalletId;
        } else {
            first = toWalletId; second = fromWalletId;
        }
        acquireLockOrFail(first, "transferP2P");
        acquireLockOrFail(second, "transferP2P");
        try {
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

            // 4. Salvar wallets (actualizados) + Transaction + 2 AccountMoves
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

            // 5. Notificaciones PUSH (stub log)
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

            log.info("✅ TransferP2P exitosa tx={} from={} to={} amount={}",
                    savedTx.id(), fromWalletId, toWalletId, amount);
            return savedTx;
        } finally {
            walletLock.unlock(first);
            walletLock.unlock(second);
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

    private void acquireLockOrFail(WalletId id, String op) {
        if (!walletLock.tryLock(id)) {
            throw new BusinessRuleViolationException("WALLET_LOCK_CONFLICT",
                    "%s no pudo adquirir lock distribuido para wallet %s. Reintente en %ds."
                            .formatted(op, id, WalletLockAdapter.TTL_SECONDS));
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
