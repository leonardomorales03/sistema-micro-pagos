package com.micropay.backend.infrastructure.persistence.postgres;

import com.micropay.backend.domain.entities.AccountMove;
import com.micropay.backend.domain.entities.Transaction;
import com.micropay.backend.domain.ports.out.TransactionRepository;
import com.micropay.backend.domain.valueobjects.*;
import com.micropay.backend.infrastructure.persistence.jpa.entities.AccountMoveJpa;
import com.micropay.backend.infrastructure.persistence.jpa.entities.TransactionJpa;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.AccountMoveJpaRepository;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.TransactionJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PostgresTransactionRepository implements TransactionRepository {

    private final TransactionJpaRepository txJpa;
    private final AccountMoveJpaRepository moveJpa;

    public PostgresTransactionRepository(TransactionJpaRepository txJpa,
                                         AccountMoveJpaRepository moveJpa) {
        this.txJpa = txJpa;
        this.moveJpa = moveJpa;
    }

    @Override
    @Transactional
    public Transaction save(Transaction transaction) {
        TransactionJpa txSaved = txJpa.save(toTxJpa(transaction));
        List<AccountMove> movesSaved = new ArrayList<>();
        if (transaction.accountMoves() != null) {
            for (AccountMove m : transaction.accountMoves()) {
                AccountMoveJpa mj = toMoveJpa(m, txSaved.getId());
                AccountMoveJpa persisted = moveJpa.save(mj);
                movesSaved.add(moveToDomain(persisted));
            }
        }
        return toDomain(txSaved, movesSaved);
    }

    @Override
    public Optional<Transaction> findById(TransactionId id) {
        return txJpa.findById(id.uuid()).map(j -> {
            List<AccountMove> moves = listAccountMoves(TransactionId.from(j.getId()));
            return toDomain(j, moves);
        });
    }

    @Override
    public Optional<Transaction> findByShortCode(ShortCode shortCode) {
        return txJpa.findByShortCode(shortCode.value()).map(j -> {
            List<AccountMove> moves = listAccountMoves(TransactionId.from(j.getId()));
            return toDomain(j, moves);
        });
    }

    @Override
    public List<Transaction> listByWallet(WalletId walletId, int page, int pageSize) {
        UUID id = walletId.uuid();
        return txJpa.findByWalletFromIdOrWalletToIdOrderByCreatedAtDesc(id, id, PageRequest.of(page, pageSize))
                .stream()
                .map(j -> toDomain(j, List.of()))
                .toList();
    }

    @Override
    public List<AccountMove> listAccountMoves(TransactionId transactionId) {
        return moveJpa.findByTransactionIdOrderByCreatedAtAsc(transactionId.uuid())
                .stream()
                .map(this::moveToDomain)
                .toList();
    }

    // ──────────────── MAPPERS ────────────────

    Transaction toDomain(TransactionJpa j, List<AccountMove> moves) {
        if (j == null) return null;
        Currency cur = Currency.valueOf(j.getCurrency());
        List<AccountMove> safeMoves = moves != null ? moves : List.of();
        Transaction.Builder b = Transaction.builder()
                .id(TransactionId.from(j.getId()))
                .type(TransactionType.valueOf(j.getType().name()))
                .shortCode(j.getShortCode() != null ? new ShortCode(j.getShortCode()) : ShortCode.random())
                .grossAmount(Money.of(j.getGrossAmount(), cur))
                .netAmount(Money.of(j.getNetAmount(), cur))
                .feeAmount(Money.of(j.getFeeAmount(), cur))
                .status(TransactionStatus.valueOf(j.getStatus().name()))
                .externalRef(j.getExternalRef())
                .createdAt(j.getCreatedAt())
                .updatedAt(j.getUpdatedAt())
                .createdBy(j.getCreatedBy())
                .failureReason(j.getFailureReason())
                .note(j.getNote())
                .accountMoves(safeMoves);
        if (j.getWalletFromId() != null) b.walletFromId(WalletId.from(j.getWalletFromId()));
        if (j.getWalletToId() != null) b.walletToId(WalletId.from(j.getWalletToId()));
        return b.build();
    }

    TransactionJpa toTxJpa(Transaction d) {
        if (d == null) return null;
        TransactionJpa j = new TransactionJpa();
        j.setId(d.id().uuid());
        j.setType(TransactionJpa.TxType.valueOf(d.type().name()));
        j.setShortCode(d.shortCode() != null ? d.shortCode().value() : null);
        j.setWalletFromId(d.walletFromId() != null ? d.walletFromId().uuid() : null);
        j.setWalletToId(d.walletToId() != null ? d.walletToId().uuid() : null);
        j.setGrossAmount(d.grossAmount().amount());
        j.setNetAmount(d.netAmount().amount());
        j.setFeeAmount(d.feeAmount().amount());
        j.setCurrency(d.grossAmount().currency().isoCode());
        j.setStatus(TransactionJpa.TxStatus.valueOf(d.status().name()));
        j.setExternalRef(d.externalRef());
        j.setCreatedBy(d.createdBy());
        j.setCreatedAt(d.createdAt());
        j.setUpdatedAt(d.updatedAt());
        j.setFailureReason(d.failureReason());
        j.setNote(d.note());
        return j;
    }

    AccountMove moveToDomain(AccountMoveJpa j) {
        if (j == null) return null;
        Currency cur = inferCurrency(j.getTransactionId());
        return new AccountMove(
                j.getId(),
                WalletId.from(j.getWalletId()),
                TransactionId.from(j.getTransactionId()),
                Money.of(j.getAmount(), cur),
                Money.of(j.getBalanceAfter(), cur),
                j.getCreatedAt()
        );
    }

    AccountMoveJpa toMoveJpa(AccountMove d, UUID txIdOverride) {
        if (d == null) return null;
        AccountMoveJpa j = new AccountMoveJpa();
        UUID id = d.id() != null ? d.id() : UUID.randomUUID();
        j.setId(id);
        j.setWalletId(d.walletId().uuid());
        UUID txId = d.transactionId() != null ? d.transactionId().uuid() : txIdOverride;
        j.setTransactionId(txId);
        j.setAmount(d.amount().amount());
        j.setBalanceAfter(d.balanceAfter().amount());
        j.setCreatedAt(d.createdAt());
        return j;
    }

    /**
     * Currency se obtiene desde Transaction (no replicamos moneda en cada AccountMove
     * para mantener normalización). Fallback: USD si no se puede inferir (casos test).
     */
    private Currency inferCurrency(UUID txId) {
        try {
            return txJpa.findById(txId)
                    .map(t -> Currency.valueOf(t.getCurrency()))
                    .orElse(Currency.USD);
        } catch (Exception e) {
            return Currency.USD;
        }
    }
}
