package com.micropay.backend.infrastructure.persistence.jpa.repositories;

import com.micropay.backend.infrastructure.persistence.jpa.entities.SubscriptionJpa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface SubscriptionJpaRepository extends JpaRepository<SubscriptionJpa, UUID> {

    Page<SubscriptionJpa> findBySubscriberIdOrderByCreatedAtDesc(UUID subscriberId, Pageable pageable);

    Page<SubscriptionJpa> findByPlanIdOrderByCreatedAtDesc(UUID planId, Pageable pageable);

    /** Elegibles para cobro: estado en {@code statuses} y next_billing_date ≤ now. */
    Page<SubscriptionJpa> findByStatusInAndNextBillingDateLessThanEqualOrderByNextBillingDateAsc(
            Collection<SubscriptionJpa.SubscriptionStatusJpa> statuses, Instant now, Pageable pageable);
}
