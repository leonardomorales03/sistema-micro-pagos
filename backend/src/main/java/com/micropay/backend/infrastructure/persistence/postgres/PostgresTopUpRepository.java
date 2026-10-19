package com.micropay.backend.infrastructure.persistence.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micropay.backend.domain.entities.TopUp;
import com.micropay.backend.domain.ports.out.TopUpRepository;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.TopUpId;
import com.micropay.backend.domain.valueobjects.TransactionId;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;
import com.micropay.backend.infrastructure.persistence.jpa.entities.TopUpJpa;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.TopUpJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class PostgresTopUpRepository implements TopUpRepository {

    private static final Logger log = LoggerFactory.getLogger(PostgresTopUpRepository.class);
    private final TopUpJpaRepository jpa;
    private final ObjectMapper json;

    public PostgresTopUpRepository(TopUpJpaRepository jpa, ObjectMapper json) {
        this.jpa = jpa;
        this.json = json;
    }

    @Override
    public TopUp save(TopUp topUp) {
        TopUpJpa j = toJpa(topUp);
        return toDomain(jpa.save(j));
    }

    @Override
    public Optional<TopUp> findById(TopUpId id) {
        return jpa.findById(id.uuid()).map(this::toDomain);
    }

    @Override
    public Optional<TopUp> findByProviderRef(String providerRef) {
        return jpa.findByProviderRef(providerRef).map(this::toDomain);
    }

    @Override
    public List<TopUp> listByWallet(WalletId walletId, int page, int pageSize) {
        Pageable p = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, pageSize)));
        Page<TopUpJpa> rs = jpa.findByWalletIdOrderByCreatedAtDesc(walletId.uuid(), p);
        return rs.getContent().stream().map(this::toDomain).toList();
    }

    @Override
    public List<TopUp> listByUser(UserId userId, int page, int pageSize) {
        Pageable p = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, pageSize)));
        Page<TopUpJpa> rs = jpa.findByUserIdOrderByCreatedAtDesc(userId.uuid(), p);
        return rs.getContent().stream().map(this::toDomain).toList();
    }

    // ──────────────────────── Mappers Domain ↔ JPA ────────────────────────

    private TopUpJpa toJpa(TopUp t) {
        TopUpJpa j = new TopUpJpa();
        j.setId(t.id().uuid());
        j.setUserId(t.userId().uuid());
        j.setWalletId(t.walletId().uuid());
        Currency cur = t.grossAmount().currency();
        j.setGrossAmount(t.grossAmount().amount());
        j.setNetAmount(t.netAmount().amount());
        j.setFeeAmount(t.feeAmount().amount());
        j.setCurrency(cur.isoCode());
        j.setProvider(t.provider());
        j.setProviderRef(t.providerRef());
        j.setCheckoutUrl(t.checkoutUrl());
        j.setStatus(TopUpJpa.TopUpStatus.valueOf(t.status().name()));
        j.setFailureReason(t.failureReason());
        j.setPaidAt(t.paidAt());
        j.setTransactionRef(t.transactionRef() != null ? t.transactionRef().uuid() : null);
        j.setSuccessUrl(t.successUrl());
        j.setCancelUrl(t.cancelUrl());
        j.setIdempotencyKey(t.idempotencyKey());
        j.setCreatedBy(t.createdBy());
        j.setCreatedAt(t.createdAt());
        j.setUpdatedAt(t.updatedAt());
        if (t.metadata() != null && !t.metadata().isEmpty()) {
            try {
                j.setMetadataJson(json.writeValueAsString(t.metadata()));
            } catch (JsonProcessingException e) {
                log.warn("toJpa metadata serialize fail {}", e.getMessage());
                j.setMetadataJson(null);
            }
        }
        return j;
    }

    private TopUp toDomain(TopUpJpa j) {
        if (j == null) return null;
        Currency cur = Currency.valueOf(j.getCurrency());
        Map<String,String> md = new HashMap<>();
        if (j.getMetadataJson() != null && !j.getMetadataJson().isBlank()) {
            try {
                md = json.readValue(j.getMetadataJson(), new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("toDomain metadata parse fail {}", e.getMessage());
            }
        }
        TopUp.Builder b = TopUp.builder()
                .id(TopUpId.from(j.getId()))
                .userId(UserId.from(j.getUserId()))
                .walletId(WalletId.from(j.getWalletId()))
                .grossAmount(Money.of(j.getGrossAmount(), cur))
                .netAmount(Money.of(j.getNetAmount(), cur))
                .feeAmount(Money.of(j.getFeeAmount(), cur))
                .provider(j.getProvider())
                .providerRef(j.getProviderRef())
                .checkoutUrl(j.getCheckoutUrl())
                .status(TopUp.Status.valueOf(j.getStatus().name()))
                .failureReason(j.getFailureReason())
                .paidAt(j.getPaidAt())
                .transactionRef(j.getTransactionRef() != null ? TransactionId.from(j.getTransactionRef()) : null)
                .successUrl(j.getSuccessUrl())
                .cancelUrl(j.getCancelUrl())
                .idempotencyKey(j.getIdempotencyKey())
                .metadata(md)
                .createdBy(j.getCreatedBy())
                .createdAt(j.getCreatedAt())
                .updatedAt(j.getUpdatedAt());
        return b.build();
    }
}
