package com.micropay.backend.application.services;

import com.micropay.backend.domain.entities.Plan;
import com.micropay.backend.domain.entities.User;
import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.exceptions.UserNotFoundException;
import com.micropay.backend.domain.ports.out.PlanRepository;
import com.micropay.backend.domain.ports.out.UserRepository;
import com.micropay.backend.domain.valueobjects.BillingInterval;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.PlanId;
import com.micropay.backend.domain.valueobjects.PlanStatus;
import com.micropay.backend.domain.valueobjects.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Casos de uso de Planes de suscripción (Requirement 22.1) — T20.
 * El "comercio" es cualquier usuario autenticado (el proyecto sólo define ROLE_USER/ROLE_ADMIN).
 */
@Service
@Transactional(readOnly = true)
public class PlanService {

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);

    private final PlanRepository plans;
    private final UserRepository users;

    public PlanService(PlanRepository plans, UserRepository users) {
        this.plans = plans;
        this.users = users;
    }

    @Transactional(rollbackFor = Exception.class)
    public Plan createPlan(UserId merchantId,
                           String name,
                           Money amount,
                           BillingInterval interval,
                           int intervalCount,
                           Integer trialDays) {
        User merchant = users.findById(merchantId)
                .orElseThrow(() -> new UserNotFoundException(merchantId.uuid()));
        merchant.assertCanOperate("crear plan");

        Plan plan = Plan.builder()
                .merchantUserId(merchantId)
                .name(name)
                .amount(amount)
                .interval(interval)
                .intervalCount(intervalCount)
                .trialDays(trialDays)
                .status(PlanStatus.ACTIVE)
                .build();
        plan = plans.save(plan);

        log.info("[PLAN] Creado id={} merchant={} name='{}' amount={} interval={}x{} trialDays={}",
                plan.id(), merchantId, plan.name(), plan.amount(), plan.interval(), plan.intervalCount(), plan.trialDays());
        return plan;
    }

    public List<Plan> listByMerchant(UserId merchantId, int page, int pageSize) {
        return plans.listByMerchant(merchantId, page, pageSize);
    }

    public Plan getPlan(PlanId id) {
        return plans.findById(id)
                .orElseThrow(() -> new BusinessRuleViolationException("PLAN_NOT_FOUND",
                        "Plan " + id + " no encontrado"));
    }

    @Transactional(rollbackFor = Exception.class)
    public Plan archivePlan(UserId merchantId, PlanId id) {
        Plan plan = getOwnedPlan(merchantId, id);
        plan.archive();
        Plan saved = plans.save(plan);
        log.info("[PLAN] Archivado id={} merchant={}", saved.id(), merchantId);
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public Plan activatePlan(UserId merchantId, PlanId id) {
        Plan plan = getOwnedPlan(merchantId, id);
        plan.activate();
        Plan saved = plans.save(plan);
        log.info("[PLAN] Activado id={} merchant={}", saved.id(), merchantId);
        return saved;
    }

    private Plan getOwnedPlan(UserId merchantId, PlanId id) {
        Plan plan = getPlan(id);
        if (!plan.merchantUserId().equals(merchantId)) {
            throw new BusinessRuleViolationException("PLAN_NOT_OWNED_BY_MERCHANT",
                    "Plan %s no pertenece al comercio %s".formatted(id, merchantId));
        }
        return plan;
    }
}
