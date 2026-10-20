package com.micropay.backend.infrastructure.persistence.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micropay.backend.domain.entities.Withdrawal;
import com.micropay.backend.domain.ports.out.WithdrawalRepository;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.PayoutDestinationId;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;
import com.micropay.backend.domain.valueobjects.WithdrawalId;
import com.micropay.backend.infrastructure.persistence.jpa.entities.WithdrawalJpa;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.WithdrawalJpaRepository;
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
public class PostgresWithdrawalRepository implements WithdrawalRepository {

    private static final Logger log = LoggerFactory.getLogger(PostgresWithdrawalRepository.class);
    private final WithdrawalJpaRepository jpa;
    private final ObjectMapper json;

    public PostgresWithdrawalRepository(WithdrawalJpaRepository jpa, ObjectMapper json) {
        this.jpa = jpa;
        this.json = json;
    }

    @Override
    public Withdrawal save(Withdrawal w) {
        return toDomain(jpa.save(toJpa(w)));
    }

    @Override
    public Optional<Withdrawal> findById(WithdrawalId id) {
        return jpa.findById(id.uuid()).map(this::toDomain);
    }

    @Override
    public Optional<Withdrawal> findByProviderRef(String providerRef) {
        return jpa.findByProviderRef(providerRef).map(this::toDomain);
    }

    @Override
    public List<Withdrawal> listByWallet(WalletId walletId, int page, int pageSize) {
        Pageable p = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, pageSize)));
        Page<WithdrawalJpa> rs = jpa.findByWalletIdOrderByRequestedAtDesc(walletId.uuid(), p);
        return rs.getContent().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Withdrawal> listByUser(UserId userId, int page, int pageSize) {
        Pageable p = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, pageSize)));
        Page<WithdrawalJpa> rs = jpa.findByUserIdOrderByRequestedAtDesc(userId.uuid(), p);
        return rs.getContent().stream().map(this::toDomain).toList();
    }

    // ──────────────────────── Mappers Domain ↔ JPA ────────────────────────

    private WithdrawalJpa toJpa(Withdrawal w) {
        WithdrawalJpa j = new WithdrawalJpa();
        j.setId(w.id().uuid());
        j.setUserId(w.userId().uuid());
        j.setWalletId(w.walletId().uuid());
        j.setPayoutDestinationId(w.payoutDestinationId().uuid());
        Currency cur = w.grossAmount().currency();
        j.setGrossAmount(w.grossAmount().amount());
        j.setNetAmount(w.netAmount().amount());
        j.setFeeAmount(w.feeAmount().amount());
        j.setCurrency(cur.isoCode());
        j.setProvider(w.provider());
        j.setProviderRef(w.providerRef());
        j.setStatus(WithdrawalJpa.WithdrawalStatus.valueOf(w.status().name()));
        j.setFailureReason(w.failureReason());
        j.setNote(w.note());
        j.setCreatedBy(w.createdBy());
        j.setRequestedAt(w.requestedAt());
        j.setProcessedAt(w.processedAt());
        j.setUpdatedAt(w.updatedAt());
        if (w.metadata() != null && !w.metadata().isEmpty()) {
            try {
                j.setMetadataJson(json.writeValueAsString(w.metadata()));
            } catch (JsonProcessingException e) {
                log.warn("toJpa withdrawal metadata serialize fail {}", e.getMessage());
            }
        }
        return j;
    }

    private Withdrawal toDomain(WithdrawalJpa j) {
        if (j == null) return null;
        Currency cur = Currency.valueOf(j.getCurrency());
        Map<String,String> md = new HashMap<>();
        if (j.getMetadataJson() != null && !j.getMetadataJson().isBlank()) {
            try {
                md = json.readValue(j.getMetadataJson(), new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("toDomain withdrawal metadata parse fail {}", e.getMessage());
            }
        }
        return Withdrawal.builder()
                .id(WithdrawalId.from(j.getId()))
                .userId(UserId.from(j.getUserId()))
                .walletId(WalletId.from(j.getWalletId()))
                .payoutDestinationId(PayoutDestinationId.from(j.getPayoutDestinationId()))
                .grossAmount(Money.of(j.getGrossAmount(), cur))
                .netAmount(Money.of(j.getNetAmount(), cur))
                .feeAmount(Money.of(j.getFeeAmount(), cur))
                .provider(j.getProvider())
                .providerRef(j.getProviderRef())
                .status(Withdrawal.Status.valueOf(j.getStatus().name()))
                .failureReason(j.getFailureReason())
                .note(j.getNote())
                .metadata(md)
                .createdBy(j.getCreatedBy())
                .requestedAt(j.getRequestedAt())
                .processedAt(j.getProcessedAt())
                .updatedAt(j.getUpdatedAt())
                .build();
    }
}
