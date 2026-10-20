package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.entities.Plan;
import com.micropay.backend.domain.valueobjects.PlanId;
import com.micropay.backend.domain.valueobjects.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Port OUT — Persistencia de Planes de suscripción (Requirement 22.1).
 */
public interface PlanRepository {

    Plan save(Plan plan);

    Optional<Plan> findById(PlanId id);

    /** Planes ofrecidos por un comercio (más recientes primero). */
    List<Plan> listByMerchant(UserId merchantUserId, int page, int pageSize);
}
