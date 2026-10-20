package com.micropay.backend.infrastructure.persistence.postgres;

import com.micropay.backend.domain.entities.AccountMove;
import com.micropay.backend.domain.entities.Transaction;
import com.micropay.backend.domain.ports.out.TransactionRepository;
import com.micropay.backend.domain.valueobjects.*;
import com.micropay.backend.infrastructure.persistence.jpa.entities.AccountMoveJpa;
import com.micropay.backend.infrastructure.persistence.jpa.entities.TransactionJpa;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.AccountMoveJpaRepository;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.TransactionJpaRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PostgresTransactionRepository implements TransactionRepository {

    private static final Logger log = LoggerFactory.getLogger(PostgresTransactionRepository.class);
    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {};

    private final TransactionJpaRepository txJpa;
    private final AccountMoveJpaRepository moveJpa;
    private final ObjectMapper json;

    public PostgresTransactionRepository(TransactionJpaRepository txJpa,
                                         AccountMoveJpaRepository moveJpa,
                                         ObjectMapper json) {
        this.txJpa = txJpa;
        this.moveJpa = moveJpa;
        this.json = json;
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
    public List<Transaction> search(UUID walletId, String type, String status, String currency,
                                    java.time.Instant from, java.time.Instant to,
                                    int page, int pageSize) {
        TransactionJpa.TxType txType = type == null || type.isBlank() ? null : TransactionJpa.TxType.valueOf(type.trim().toUpperCase());
        TransactionJpa.TxStatus txStatus = status == null || status.isBlank() ? null : TransactionJpa.TxStatus.valueOf(status.trim().toUpperCase());
        String cur = currency == null || currency.isBlank() ? null : currency.trim().toUpperCase();
        int p = Math.max(0, page);
        int size = Math.min(200, Math.max(1, pageSize));
        return txJpa.search(walletId, txType, txStatus, cur, from, to, PageRequest.of(p, size))
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

    @Override
    public List<com.micropay.backend.domain.valueobjects.AnnualTaxLine> annualTaxSummary(
            java.time.Instant from, java.time.Instant to) {
        return txJpa.annualTaxSummary(from, to, TransactionJpa.TxStatus.COMPLETED)
                .stream()
                .map(row -> new com.micropay.backend.domain.valueobjects.AnnualTaxLine(
                        (UUID) row[0],
                        row[1] != null ? row[1].toString() : null,
                        (String) row[2],
                        (java.math.BigDecimal) row[3],
                        (java.math.BigDecimal) row[4],
                        (java.math.BigDecimal) row[5],
                        row[6] != null ? ((Number) row[6]).longValue() : 0L))
                .toList();
    }

    @Override
    @Transactional
    public void updateStatus(TransactionId id, com.micropay.backend.domain.valueobjects.TransactionStatus status) {
        txJpa.findById(id.uuid()).ifPresent(j -> {
            j.setStatus(TransactionJpa.TxStatus.valueOf(status.name()));
            j.setUpdatedAt(java.time.Instant.now());
            txJpa.save(j);
        });
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
                .metadata(readMetadata(j.getMetadata()))
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
        j.setMetadata(writeMetadata(d.metadata()));
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

    /** Serializa el mapa de metadatos a JSONB; mapa vacío se persiste como NULL. */
    private String writeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return null;
        try {
            return json.writeValueAsString(metadata);
        } catch (Exception e) {
            log.warn("No se pudo serializar metadata de transacción: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> readMetadata(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        try {
            return json.readValue(raw, METADATA_TYPE);
        } catch (Exception e) {
            log.warn("Metadata de transacción ilegible, se ignora: {}", e.getMessage());
            return Map.of();
        }
    }
}
