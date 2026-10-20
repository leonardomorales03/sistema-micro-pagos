package com.micropay.backend.infrastructure.persistence.postgres;

import com.micropay.backend.domain.entities.PaymentRequest;
import com.micropay.backend.domain.ports.out.PaymentRequestRepository;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.PaymentRequestId;
import com.micropay.backend.domain.valueobjects.PaymentRequestStatus;
import com.micropay.backend.domain.valueobjects.ShortCode;
import com.micropay.backend.domain.valueobjects.TransactionId;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;
import com.micropay.backend.infrastructure.persistence.jpa.entities.PaymentRequestJpa;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.PaymentRequestJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Adaptador Postgres del puerto {@link PaymentRequestRepository}.
 * Mapea el agregado de dominio {@link PaymentRequest} ↔ entidad JPA {@link PaymentRequestJpa}
 * (tabla `payment_requests` creada en V1, ampliada en V5).
 */
@Repository
public class PostgresPaymentRequestRepository implements PaymentRequestRepository {

    private final PaymentRequestJpaRepository jpa;

    public PostgresPaymentRequestRepository(PaymentRequestJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public PaymentRequest save(PaymentRequest pr) {
        return toDomain(jpa.save(toJpa(pr)));
    }

    @Override
    public Optional<PaymentRequest> findById(PaymentRequestId id) {
        return jpa.findById(id.uuid()).map(this::toDomain);
    }

    @Override
    public Optional<PaymentRequest> findByShortCode(ShortCode shortCode) {
        return jpa.findByShortCode(shortCode.value()).map(this::toDomain);
    }

    @Override
    public boolean existsByShortCode(ShortCode shortCode) {
        return jpa.existsByShortCode(shortCode.value());
    }

    @Override
    public List<PaymentRequest> listByMerchant(UserId merchantUserId, int page, int pageSize) {
        Pageable p = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, pageSize)));
        Page<PaymentRequestJpa> rs = jpa.findByCreatedByOrderByCreatedAtDesc(merchantUserId.uuid(), p);
        return rs.getContent().stream().map(this::toDomain).toList();
    }

    // ──────────────────────── Mappers Domain ↔ JPA ────────────────────────

    private PaymentRequestJpa toJpa(PaymentRequest pr) {
        PaymentRequestJpa j = new PaymentRequestJpa();
        j.setId(pr.id().uuid());
        j.setWalletToId(pr.merchantWalletId().uuid());
        j.setShortCode(pr.shortCode().value());
        j.setAmount(pr.amount().amount());
        j.setCurrency(pr.amount().currency().isoCode());
        j.setDescription(pr.description());
        j.setOrderReference(pr.orderReference());
        j.setWebhookUrl(pr.webhookUrl());
        j.setStatus(PaymentRequestJpa.PayReqStatus.valueOf(pr.status().name()));
        j.setPaidTransactionId(pr.paidWithTxId() == null ? null : pr.paidWithTxId().uuid());
        j.setCreatedBy(pr.merchantUserId().uuid());
        j.setExpiresAt(pr.expiresAt());
        j.setPaidAt(pr.paidAt());
        j.setCreatedAt(pr.createdAt());
        j.setUpdatedAt(pr.paidAt() != null ? pr.paidAt() : pr.createdAt());
        return j;
    }

    private PaymentRequest toDomain(PaymentRequestJpa j) {
        if (j == null) return null;
        Currency cur = Currency.valueOf(j.getCurrency());
        return PaymentRequest.builder()
                .id(PaymentRequestId.from(j.getId()))
                .merchantUserId(UserId.from(j.getCreatedBy()))
                .merchantWalletId(WalletId.from(j.getWalletToId()))
                .amount(Money.of(j.getAmount(), cur))
                .description(j.getDescription())
                .orderReference(j.getOrderReference())
                .shortCode(new ShortCode(j.getShortCode()))
                .webhookUrl(j.getWebhookUrl())
                .status(PaymentRequestStatus.valueOf(j.getStatus().name()))
                .paidWithTxId(j.getPaidTransactionId() == null ? null : TransactionId.from(j.getPaidTransactionId()))
                .expiresAt(j.getExpiresAt())
                .createdAt(j.getCreatedAt())
                .paidAt(j.getPaidAt())
                .build();
    }
}
