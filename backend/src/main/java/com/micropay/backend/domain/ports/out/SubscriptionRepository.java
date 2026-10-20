package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.entities.Subscription;
import com.micropay.backend.domain.valueobjects.PlanId;
import com.micropay.backend.domain.valueobjects.SubscriptionId;
import com.micropay.backend.domain.valueobjects.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Port OUT — Persistencia de Subscriptions (Requirement 22.3).
 */
public interface SubscriptionRepository {

    Subscription save(Subscription subscription);

    Optional<Subscription> findById(SubscriptionId id);

    List<Subscription> listBySubscriber(UserId subscriberUserId, int page, int pageSize);

    /** Suscriptores de un plan concreto (para vistas de comercio). */
    List<Subscription> listByPlan(PlanId planId, int page, int pageSize);

    /**
     * Suscripciones elegibles para cobro: status ACTIVE/TRIALING y next_billing_date ≤ now.
     * Usado por el scheduler de facturación (requiere ShedLock para evitar doble ejecución).
     */
    List<Subscription> findDueForBilling(Instant now, int limit);
}
