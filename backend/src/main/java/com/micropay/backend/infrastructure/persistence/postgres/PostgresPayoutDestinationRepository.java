package com.micropay.backend.infrastructure.persistence.postgres;

import com.micropay.backend.domain.entities.PayoutDestination;
import com.micropay.backend.domain.ports.out.PayoutDestinationRepository;
import com.micropay.backend.domain.valueobjects.PayoutDestinationId;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.infrastructure.persistence.jpa.entities.PayoutDestinationJpa;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.PayoutDestinationJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PostgresPayoutDestinationRepository implements PayoutDestinationRepository {

    private final PayoutDestinationJpaRepository jpa;

    public PostgresPayoutDestinationRepository(PayoutDestinationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public PayoutDestination save(PayoutDestination d) {
        return toDomain(jpa.save(toJpa(d)));
    }

    @Override
    public Optional<PayoutDestination> findById(PayoutDestinationId id) {
        return jpa.findById(id.uuid()).map(this::toDomain);
    }

    @Override
    public List<PayoutDestination> listByUser(UserId userId) {
        return jpa.findByUserIdOrderByCreatedAtDesc(userId.uuid()).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<PayoutDestination> findDefaultByUser(UserId userId) {
        return jpa.findByUserIdAndIsDefaultTrue(userId.uuid()).map(this::toDomain);
    }

    @Override
    public Optional<PayoutDestination> findByProviderTokenRef(String providerTokenRef) {
        return jpa.findByProviderTokenRef(providerTokenRef).map(this::toDomain);
    }

    @Override
    public void deleteById(PayoutDestinationId id) {
        jpa.deleteById(id.uuid());
    }

    @Override
    public long countByUser(UserId userId) {
        return jpa.countByUserId(userId.uuid());
    }

    // ──────────────────────── Mappers Domain ↔ JPA ────────────────────────

    private PayoutDestinationJpa toJpa(PayoutDestination d) {
        PayoutDestinationJpa j = new PayoutDestinationJpa();
        j.setId(d.id().uuid());
        j.setUserId(d.userId().uuid());
        j.setType(PayoutDestinationJpa.DestType.valueOf(d.type().name()));
        j.setMaskedLabel(d.maskedLabel());
        j.setBankName(d.bankName());
        j.setAccountType(d.accountType());
        j.setAccountNumberMasked(d.accountNumberMasked());
        j.setLast4(d.last4());
        j.setHolderName(d.holderName());
        j.setCountry(d.country());
        j.setCurrency(d.currency());
        j.setDefault(d.isDefault());
        j.setVerified(d.verified());
        j.setProviderTokenRef(d.providerTokenRef());
        j.setCreatedAt(d.createdAt());
        j.setUpdatedAt(d.updatedAt());
        return j;
    }

    private PayoutDestination toDomain(PayoutDestinationJpa j) {
        if (j == null) return null;
        return PayoutDestination.builder()
                .id(PayoutDestinationId.from(j.getId()))
                .userId(UserId.from(j.getUserId()))
                .type(PayoutDestination.Type.valueOf(j.getType().name()))
                .maskedLabel(j.getMaskedLabel())
                .bankName(j.getBankName())
                .accountType(j.getAccountType())
                .accountNumberMasked(j.getAccountNumberMasked())
                .last4(j.getLast4())
                .holderName(j.getHolderName())
                .country(j.getCountry())
                .currency(j.getCurrency())
                .isDefault(j.isDefault())
                .verified(j.isVerified())
                .providerTokenRef(j.getProviderTokenRef())
                .createdAt(j.getCreatedAt())
                .updatedAt(j.getUpdatedAt())
                .build();
    }
}
