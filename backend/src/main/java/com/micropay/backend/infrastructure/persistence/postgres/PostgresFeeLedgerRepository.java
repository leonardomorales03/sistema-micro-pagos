package com.micropay.backend.infrastructure.persistence.postgres;

import com.micropay.backend.domain.entities.FeeLedgerEntry;
import com.micropay.backend.domain.ports.out.FeeLedgerRepository;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.FeeType;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.TransactionId;
import com.micropay.backend.infrastructure.persistence.jpa.entities.FeeLedgerJpa;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.FeeLedgerJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PostgresFeeLedgerRepository implements FeeLedgerRepository {

    private final FeeLedgerJpaRepository jpa;

    public PostgresFeeLedgerRepository(FeeLedgerJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public FeeLedgerEntry save(FeeLedgerEntry entry) {
        return toDomain(jpa.save(toJpa(entry)));
    }

    @Override
    public List<FeeLedgerEntry> listByTransaction(TransactionId transactionId) {
        return jpa.findByTransactionIdOrderByCreatedAtAsc(transactionId.uuid()).stream()
                .map(this::toDomain)
                .toList();
    }

    // ──────────────── Mappers ────────────────

    private FeeLedgerJpa toJpa(FeeLedgerEntry e) {
        FeeLedgerJpa j = new FeeLedgerJpa();
        j.setId(e.id());
        j.setTransactionId(e.transactionId().uuid());
        j.setAmount(e.amount().amount());
        j.setCurrency(e.amount().currency().isoCode());
        j.setFeeType(FeeLedgerJpa.FeeTypeJpa.valueOf(e.feeType().name()));
        j.setRuleVersion(e.ruleVersion());
        j.setDescription(e.description());
        j.setCreatedAt(e.createdAt());
        return j;
    }

    private FeeLedgerEntry toDomain(FeeLedgerJpa j) {
        if (j == null) return null;
        Currency cur = Currency.valueOf(j.getCurrency());
        return new FeeLedgerEntry(
                j.getId(),
                TransactionId.from(j.getTransactionId()),
                Money.of(j.getAmount(), cur),
                FeeType.valueOf(j.getFeeType().name()),
                j.getRuleVersion(),
                j.getDescription(),
                j.getCreatedAt()
        );
    }
}
