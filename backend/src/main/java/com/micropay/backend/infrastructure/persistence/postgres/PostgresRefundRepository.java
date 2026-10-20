package com.micropay.backend.infrastructure.persistence.postgres;

import com.micropay.backend.domain.entities.Refund;
import com.micropay.backend.domain.ports.out.RefundRepository;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.RefundId;
import com.micropay.backend.domain.valueobjects.RefundType;
import com.micropay.backend.domain.valueobjects.TransactionId;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.infrastructure.persistence.jpa.entities.RefundJpa;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.RefundJpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public class PostgresRefundRepository implements RefundRepository {

    private final RefundJpaRepository jpa;

    public PostgresRefundRepository(RefundJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Refund save(Refund refund) {
        return toDomain(jpa.save(toJpa(refund)));
    }

    @Override
    public Optional<Refund> findById(RefundId id) {
        return jpa.findById(id.uuid()).map(this::toDomain);
    }

    @Override
    public List<Refund> listBySourceTransaction(TransactionId sourceTxId) {
        return jpa.findBySourceTxIdOrderByCreatedAtAsc(sourceTxId.uuid()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Money sumRefundedFor(TransactionId sourceTxId, Currency currency) {
        BigDecimal total = jpa.findBySourceTxIdOrderByCreatedAtAsc(sourceTxId.uuid()).stream()
                .map(RefundJpa::getRefundedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Money.of(total, currency);
    }

    // ──────────────── Mappers ────────────────

    private RefundJpa toJpa(Refund r) {
        RefundJpa j = new RefundJpa();
        j.setId(r.id().uuid());
        j.setSourceTxId(r.sourceTransactionId().uuid());
        j.setRefundingUserId(r.refundingUserId().uuid());
        j.setRefundedAmount(r.refundedAmount().amount());
        j.setCurrency(r.refundedAmount().currency().isoCode());
        j.setType(RefundJpa.RefundTypeJpa.valueOf(r.type().name()));
        j.setReason(r.reason());
        j.setRefundTxId(r.refundTxId().uuid());
        j.setCreatedAt(r.createdAt());
        return j;
    }

    private Refund toDomain(RefundJpa j) {
        if (j == null) return null;
        Currency cur = Currency.valueOf(j.getCurrency());
        return Refund.builder()
                .id(RefundId.from(j.getId()))
                .sourceTransactionId(TransactionId.from(j.getSourceTxId()))
                .refundingUserId(UserId.from(j.getRefundingUserId()))
                .refundedAmount(Money.of(j.getRefundedAmount(), cur))
                .type(RefundType.valueOf(j.getType().name()))
                .reason(j.getReason())
                .refundTxId(TransactionId.from(j.getRefundTxId()))
                .createdAt(j.getCreatedAt())
                .build();
    }
}
