package com.micropay.backend.infrastructure.persistence.postgres;

import com.micropay.backend.domain.entities.Subscription;
import com.micropay.backend.domain.ports.out.SubscriptionRepository;
import com.micropay.backend.domain.valueobjects.PlanId;
import com.micropay.backend.domain.valueobjects.SubscriptionId;
import com.micropay.backend.domain.valueobjects.SubscriptionStatus;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;
import com.micropay.backend.infrastructure.persistence.jpa.entities.SubscriptionJpa;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.SubscriptionJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador Postgres del puerto {@link SubscriptionRepository}.
 * Mapea {@link Subscription} ↔ {@link SubscriptionJpa} (tabla `subscriptions`, V7).
 */
@Repository
public class PostgresSubscriptionRepository implements SubscriptionRepository {

    private static final List<SubscriptionJpa.SubscriptionStatusJpa> BILLABLE =
            List.of(SubscriptionJpa.SubscriptionStatusJpa.ACTIVE,
                    SubscriptionJpa.SubscriptionStatusJpa.TRIALING);

    private final SubscriptionJpaRepository jpa;

    public PostgresSubscriptionRepository(SubscriptionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Subscription save(Subscription subscription) {
        return toDomain(jpa.save(toJpa(subscription)));
    }

    @Override
    public Optional<Subscription> findById(SubscriptionId id) {
        return jpa.findById(id.uuid()).map(this::toDomain);
    }

    @Override
    public List<Subscription> listBySubscriber(UserId subscriberUserId, int page, int pageSize) {
        Pageable p = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, pageSize)));
        Page<SubscriptionJpa> rs = jpa.findBySubscriberIdOrderByCreatedAtDesc(subscriberUserId.uuid(), p);
        return rs.getContent().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Subscription> listByPlan(PlanId planId, int page, int pageSize) {
        Pageable p = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, pageSize)));
        Page<SubscriptionJpa> rs = jpa.findByPlanIdOrderByCreatedAtDesc(planId.uuid(), p);
        return rs.getContent().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Subscription> findDueForBilling(Instant now, int limit) {
        Pageable p = PageRequest.of(0, Math.max(1, limit));
        Page<SubscriptionJpa> rs = jpa
                .findByStatusInAndNextBillingDateLessThanEqualOrderByNextBillingDateAsc(BILLABLE, now, p);
        return rs.getContent().stream().map(this::toDomain).toList();
    }

    // ──────────────────────── Mappers Domain ↔ JPA ────────────────────────

    private SubscriptionJpa toJpa(Subscription s) {
        SubscriptionJpa j = new SubscriptionJpa();
        j.setId(s.id().uuid());
        j.setPlanId(s.planId().uuid());
        j.setSubscriberId(s.subscriberUserId().uuid());
        j.setSubscriberWalletId(s.subscriberWalletId().uuid());
        j.setStatus(SubscriptionJpa.SubscriptionStatusJpa.valueOf(s.status().name()));
        j.setCurrentPeriodStart(s.currentPeriodStart());
        j.setNextBillingDate(s.nextBillingDate());
        j.setCanceledAt(s.canceledAt());
        j.setDunningAttempts(s.dunningAttempts());
        j.setLastBillingResult(s.lastBillingResult());
        j.setCreatedAt(s.createdAt());
        j.setUpdatedAt(Instant.now());
        return j;
    }

    private Subscription toDomain(SubscriptionJpa j) {
        if (j == null) return null;
        return Subscription.builder()
                .id(SubscriptionId.from(j.getId()))
                .planId(PlanId.from(j.getPlanId()))
                .subscriberUserId(UserId.from(j.getSubscriberId()))
                .subscriberWalletId(WalletId.from(j.getSubscriberWalletId()))
                .status(SubscriptionStatus.valueOf(j.getStatus().name()))
                .currentPeriodStart(j.getCurrentPeriodStart())
                .nextBillingDate(j.getNextBillingDate())
                .canceledAt(j.getCanceledAt())
                .dunningAttempts(j.getDunningAttempts())
                .lastBillingResult(j.getLastBillingResult())
                .createdAt(j.getCreatedAt())
                .build();
    }
}
