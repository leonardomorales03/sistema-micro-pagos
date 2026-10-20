package com.micropay.backend.api.controllers;

import com.micropay.backend.api.dtos.PlanDtos;
import com.micropay.backend.api.dtos.SubscriptionDtos;
import com.micropay.backend.application.services.PlanService;
import com.micropay.backend.application.services.SubscriptionService;
import com.micropay.backend.domain.entities.Plan;
import com.micropay.backend.domain.entities.Subscription;
import com.micropay.backend.domain.valueobjects.BillingInterval;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.PlanId;
import com.micropay.backend.domain.valueobjects.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Endpoints de Planes de suscripción (Requisito 22.1) — T20.
 * "Comercio" = cualquier usuario autenticado que ofrece el plan (el proyecto sólo define ROLE_USER/ROLE_ADMIN).
 */
@Tag(name = "Plans", description = "Planes de suscripción recurrente creados por el comercio")
@RestController
@RequestMapping("/api/v1/merchant/plans")
@SecurityRequirement(name = "bearerAuth")
public class PlanController {

    private final PlanService plans;
    private final SubscriptionService subscriptions;

    public PlanController(PlanService plans, SubscriptionService subscriptions) {
        this.plans = plans;
        this.subscriptions = subscriptions;
    }

    @Operation(summary = "Crear Plan",
               description = "Crea un plan de cobro recurrente. Estado inicial ACTIVE. Con trial_days > 0 " +
                       "las suscripciones entran en TRIALING y el primer cobro ocurre al final del trial.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Plan creado (ACTIVE)."),
            @ApiResponse(responseCode = "400", description = "Intervalo o moneda inválidos."),
            @ApiResponse(responseCode = "401", description = "No autenticado."),
            @ApiResponse(responseCode = "422", description = "Nombre/monto/interval_count/trial_days inválidos o usuario no operable.")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<PlanDtos.PlanResponse> create(
            @Valid @RequestBody PlanDtos.CreatePlanRequest req,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        Currency cur = Currency.valueOf(req.currency().toUpperCase());
        Money amount = Money.of(req.amount() == null ? BigDecimal.ZERO : req.amount(), cur);
        BillingInterval interval = BillingInterval.valueOf(req.interval().toUpperCase());
        int intervalCount = req.intervalCount() == null ? 1 : req.intervalCount();
        Plan plan = plans.createPlan(currentUser, req.name(), amount, interval, intervalCount, req.trialDays());
        return ResponseEntity.status(201).body(toResp(plan));
    }

    @Operation(summary = "Listar mis Planes (comercio autenticado)")
    @GetMapping
    public ResponseEntity<PlanDtos.PlanListResponse> listMine(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        List<Plan> rows = plans.listByMerchant(currentUser, page, pageSize);
        return ResponseEntity.ok(new PlanDtos.PlanListResponse(
                rows.stream().map(PlanController::toResp).toList(), page, pageSize));
    }

    @Operation(summary = "Archivar Plan",
               description = "Un plan ARCHIVED deja de aceptar nuevas suscripciones; las existentes siguen cobrándose.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plan archivado."),
            @ApiResponse(responseCode = "422", description = "Plan inexistente o no propio.")
    })
    @PostMapping("/{id}/archive")
    public ResponseEntity<PlanDtos.PlanResponse> archive(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        return ResponseEntity.ok(toResp(plans.archivePlan(currentUser, PlanId.from(id))));
    }

    @Operation(summary = "Reactivar Plan archivado")
    @PostMapping("/{id}/activate")
    public ResponseEntity<PlanDtos.PlanResponse> activate(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        return ResponseEntity.ok(toResp(plans.activatePlan(currentUser, PlanId.from(id))));
    }

    @Operation(summary = "Listar suscriptores de un Plan propio",
               description = "Devuelve las suscripciones (todos los estados) del plan, más recientes primero.")
    @GetMapping("/{id}/subscribers")
    public ResponseEntity<SubscriptionDtos.SubscriptionListResponse> subscribers(
            @PathVariable UUID id,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        List<Subscription> rows = subscriptions.listByPlan(currentUser, PlanId.from(id), page, pageSize);
        return ResponseEntity.ok(new SubscriptionDtos.SubscriptionListResponse(
                rows.stream().map(SubscriptionController::toResp).toList(), page, pageSize));
    }

    // ───────────────── Mapper ─────────────────

    static PlanDtos.PlanResponse toResp(Plan p) {
        return new PlanDtos.PlanResponse(
                p.id().uuid(),
                p.merchantUserId().uuid(),
                p.name(),
                p.amount().amount(),
                p.amount().currency().isoCode(),
                p.interval().name(),
                p.intervalCount(),
                p.trialDays(),
                p.status().name(),
                p.createdAt()
        );
    }
}
