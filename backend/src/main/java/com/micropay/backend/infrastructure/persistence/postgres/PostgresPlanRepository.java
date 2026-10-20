package com.micropay.backend.infrastructure.persistence.postgres;

import com.micropay.backend.domain.entities.Plan;
import com.micropay.backend.domain.ports.out.PlanRepository;
import com.micropay.backend.domain.valueobjects.BillingInterval;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.PlanId;
import com.micropay.backend.domain.valueobjects.PlanStatus;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.infrastructure.persistence.jpa.entities.PlanJpa;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.PlanJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Adaptador Postgres del puerto {@link PlanRepository}.
 * Mapea {@link Plan} ↔ {@link PlanJpa} (tabla `plans`, V7).
 */
@Repository
public class PostgresPlanRepository implements PlanRepository {

    private final PlanJpaRepository jpa;

    public PostgresPlanRepository(PlanJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Plan save(Plan plan) {
        return toDomain(jpa.save(toJpa(plan)));
    }

    @Override
    public Optional<Plan> findById(PlanId id) {
        return jpa.findById(id.uuid()).map(this::toDomain);
    }

    @Override
    public List<Plan> listByMerchant(UserId merchantUserId, int page, int pageSize) {
        Pageable p = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, pageSize)));
        Page<PlanJpa> rs = jpa.findByMerchantUserIdOrderByCreatedAtDesc(merchantUserId.uuid(), p);
        return rs.getContent().stream().map(this::toDomain).toList();
    }

    // ──────────────────────── Mappers Domain ↔ JPA ────────────────────────

    private PlanJpa toJpa(Plan plan) {
        PlanJpa j = new PlanJpa();
        j.setId(plan.id().uuid());
        j.setMerchantUserId(plan.merchantUserId().uuid());
        j.setName(plan.name());
        j.setAmount(plan.amount().amount());
        j.setCurrency(plan.amount().currency().isoCode());
        j.setBillingInterval(PlanJpa.BillingIntervalJpa.valueOf(plan.interval().name()));
        j.setIntervalCount(plan.intervalCount());
        j.setTrialDays(plan.trialDays());
        j.setStatus(PlanJpa.PlanStatusJpa.valueOf(plan.status().name()));
        j.setCreatedAt(plan.createdAt());
        return j;
    }

    private Plan toDomain(PlanJpa j) {
        if (j == null) return null;
        Currency cur = Currency.valueOf(j.getCurrency());
        return Plan.builder()
                .id(PlanId.from(j.getId()))
                .merchantUserId(UserId.from(j.getMerchantUserId()))
                .name(j.getName())
                .amount(Money.of(j.getAmount(), cur))
                .interval(BillingInterval.valueOf(j.getBillingInterval().name()))
                .intervalCount(j.getIntervalCount())
                .trialDays(j.getTrialDays())
                .status(PlanStatus.valueOf(j.getStatus().name()))
                .createdAt(j.getCreatedAt())
                .build();
    }
}
