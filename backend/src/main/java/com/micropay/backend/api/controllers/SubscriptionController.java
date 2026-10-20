package com.micropay.backend.api.controllers;

import com.micropay.backend.api.dtos.SubscriptionDtos;
import com.micropay.backend.application.services.SubscriptionBillingService;
import com.micropay.backend.application.services.SubscriptionService;
import com.micropay.backend.domain.entities.Subscription;
import com.micropay.backend.domain.valueobjects.PlanId;
import com.micropay.backend.domain.valueobjects.SubscriptionId;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints de Subscriptions y ciclo de facturación (Requisitos 22.2, 22.3, 22.6) — T20.
 *
 * El cobro recurrente lo ejecuta el scheduler ({@link com.micropay.backend.infrastructure.scheduler.SubscriptionBillingScheduler});
 * {@code POST /billing/run} permite dispararlo manualmente (ADMIN) para pruebas/operación.
 */
@Tag(name = "Subscriptions", description = "Suscripciones recurrentes del usuario y disparo del ciclo de facturación")
@RestController
@RequestMapping("/api/v1/subscriptions")
@SecurityRequirement(name = "bearerAuth")
public class SubscriptionController {

    private final SubscriptionService subscriptions;
    private final SubscriptionBillingService billing;

    public SubscriptionController(SubscriptionService subscriptions, SubscriptionBillingService billing) {
        this.subscriptions = subscriptions;
        this.billing = billing;
    }

    @Operation(summary = "Suscribirse a un Plan",
               description = "Aprueba el mandato de cobro recurrente sobre la wallet indicada. Si el plan tiene " +
                       "trial, la suscripción inicia en TRIALING; en caso contrario en ACTIVE.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Suscripción creada."),
            @ApiResponse(responseCode = "404", description = "Usuario o wallet no existen."),
            @ApiResponse(responseCode = "422", description = "Plan inexistente/archivado, auto-suscripción, " +
                    "wallet no propia/inactiva o moneda distinta a la del plan.")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<SubscriptionDtos.SubscriptionResponse> subscribe(
            @Valid @RequestBody SubscriptionDtos.SubscribeRequest req,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        Subscription sub = subscriptions.subscribe(
                currentUser, PlanId.from(req.planId()), WalletId.from(req.walletId()));
        return ResponseEntity.status(201).body(toResp(sub));
    }

    @Operation(summary = "Listar mis Suscripciones")
    @GetMapping
    public ResponseEntity<SubscriptionDtos.SubscriptionListResponse> listMine(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        List<Subscription> rows = subscriptions.listBySubscriber(currentUser, page, pageSize);
        return ResponseEntity.ok(new SubscriptionDtos.SubscriptionListResponse(
                rows.stream().map(SubscriptionController::toResp).toList(), page, pageSize));
    }

    @Operation(summary = "Cancelar suscripción",
               description = "Terminal e idempotente. No aplica pro-rata: el acceso se mantiene hasta el fin del período pagado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Suscripción cancelada."),
            @ApiResponse(responseCode = "422", description = "Suscripción inexistente o de otro usuario.")
    })
    @PostMapping("/{id}/cancel")
    public ResponseEntity<SubscriptionDtos.SubscriptionResponse> cancel(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        return ResponseEntity.ok(toResp(subscriptions.cancel(currentUser, SubscriptionId.from(id))));
    }

    @Operation(summary = "Pausar suscripción",
               description = "Sólo ACTIVE/TRIALING. Suspende el cobro en el siguiente ciclo (sin pro-rata).")
    @PostMapping("/{id}/pause")
    public ResponseEntity<SubscriptionDtos.SubscriptionResponse> pause(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        return ResponseEntity.ok(toResp(subscriptions.pause(currentUser, SubscriptionId.from(id))));
    }

    @Operation(summary = "Reanudar suscripción pausada", description = "Sólo PAUSED → ACTIVE.")
    @PostMapping("/{id}/resume")
    public ResponseEntity<SubscriptionDtos.SubscriptionResponse> resume(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        return ResponseEntity.ok(toResp(subscriptions.resume(currentUser, SubscriptionId.from(id))));
    }

    @Operation(summary = "Disparar ciclo de facturación (ADMIN)",
               description = "Ejecuta ahora el mismo proceso del scheduler horario: cobra las suscripciones vencidas " +
                       "y aplica la política de dunning. Cada suscripción se procesa en su propia transacción.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ciclo ejecutado (resumen scanned/succeeded/failed/canceled)."),
            @ApiResponse(responseCode = "403", description = "Requiere ROLE_ADMIN.")
    })
    @PostMapping("/billing/run")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<SubscriptionDtos.BillingRunResponse> runBilling() {
        SubscriptionBillingService.BillingRunSummary s = billing.runBillingCycle();
        return ResponseEntity.ok(new SubscriptionDtos.BillingRunResponse(
                s.scanned(), s.succeeded(), s.failed(), s.canceled()));
    }

    // ───────────────── Mapper ─────────────────

    static SubscriptionDtos.SubscriptionResponse toResp(Subscription s) {
        return new SubscriptionDtos.SubscriptionResponse(
                s.id().uuid(),
                s.planId().uuid(),
                s.subscriberUserId().uuid(),
                s.subscriberWalletId().uuid(),
                s.status().name(),
                s.currentPeriodStart(),
                s.nextBillingDate(),
                s.canceledAt(),
                s.dunningAttempts(),
                s.lastBillingResult(),
                s.createdAt()
        );
    }
}
