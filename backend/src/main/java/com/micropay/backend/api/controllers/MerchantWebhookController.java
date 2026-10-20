package com.micropay.backend.api.controllers;

import com.micropay.backend.api.dtos.MerchantWebhookDtos;
import com.micropay.backend.application.services.WebhookService;
import com.micropay.backend.domain.entities.MerchantWebhookConfig;
import com.micropay.backend.domain.entities.WebhookDelivery;
import com.micropay.backend.domain.valueobjects.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints de webhooks salientes del comercio (Requisito 25.4, 25.5) — T22.
 * Cada comercio gestiona su propia configuración, historial de entregas, reintentos y simulación.
 */
@Tag(name = "Merchant Webhooks", description = "Configuración, historial, reintentos y simulación de webhooks del comercio")
@RestController
@RequestMapping("/api/v1/merchant/webhooks")
@SecurityRequirement(name = "bearerAuth")
public class MerchantWebhookController {

    private final WebhookService webhooks;

    public MerchantWebhookController(WebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @Operation(summary = "Consultar mi configuración de webhook")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Configuración vigente."),
            @ApiResponse(responseCode = "204", description = "El comercio no tiene configuración.")
    })
    @GetMapping("/config")
    public ResponseEntity<MerchantWebhookDtos.WebhookConfigResponse> getConfig(
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        return webhooks.getConfig(currentUser)
                .map(cfg -> ResponseEntity.ok(toResp(cfg)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Crear o actualizar mi configuración de webhook",
               description = "Al crear se genera un signing_secret; usar rotate_secret=true para rotarlo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Configuración guardada."),
            @ApiResponse(responseCode = "400", description = "endpoint_url inválida (se requiere http/https).")
    })
    @PutMapping("/config")
    public ResponseEntity<MerchantWebhookDtos.WebhookConfigResponse> upsertConfig(
            @Valid @RequestBody MerchantWebhookDtos.WebhookConfigRequest req,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        MerchantWebhookConfig cfg = webhooks.upsertConfig(
                currentUser,
                req.endpointUrl(),
                req.subscribedEvents(),
                Boolean.TRUE.equals(req.rotateSecret()));
        return ResponseEntity.ok(toResp(cfg));
    }

    @Operation(summary = "Eliminar mi configuración de webhook")
    @DeleteMapping("/config")
    public ResponseEntity<Void> deleteConfig(@Parameter(hidden = true) @CurrentUser UserId currentUser) {
        webhooks.deleteConfig(currentUser);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Historial de entregas de webhook")
    @GetMapping("/deliveries")
    public ResponseEntity<MerchantWebhookDtos.WebhookDeliveryListResponse> listDeliveries(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        List<WebhookDelivery> rows = webhooks.listDeliveries(currentUser, page, pageSize);
        return ResponseEntity.ok(new MerchantWebhookDtos.WebhookDeliveryListResponse(
                rows.stream().map(MerchantWebhookController::toResp).toList(), page, pageSize));
    }

    @Operation(summary = "Reenviar manualmente una entrega")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reintento ejecutado (ver status)."),
            @ApiResponse(responseCode = "422", description = "Entrega inexistente, de otro comercio o sin configuración.")
    })
    @PostMapping("/deliveries/{id}/redeliver")
    public ResponseEntity<MerchantWebhookDtos.WebhookDeliveryResponse> redeliver(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        return ResponseEntity.ok(toResp(webhooks.redeliver(currentUser, id)));
    }

    @Operation(summary = "Disparar un evento de prueba a mi endpoint",
               description = "Sandbox: permite verificar el handler del comercio (Requisito 25.5).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evento de prueba entregado (ver status)."),
            @ApiResponse(responseCode = "422", description = "Sin configuración de webhook."),
            @ApiResponse(responseCode = "400", description = "event_type desconocido.")
    })
    @PostMapping("/simulate")
    public ResponseEntity<MerchantWebhookDtos.WebhookDeliveryResponse> simulate(
            @Valid @RequestBody MerchantWebhookDtos.WebhookSimulateRequest req,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        return ResponseEntity.ok(toResp(webhooks.simulate(currentUser, req.eventType())));
    }

    // ───────────────── Mappers ─────────────────

    static MerchantWebhookDtos.WebhookConfigResponse toResp(MerchantWebhookConfig cfg) {
        return new MerchantWebhookDtos.WebhookConfigResponse(
                cfg.id(),
                cfg.endpointUrl(),
                cfg.signingSecret(),
                cfg.subscribedEvents(),
                cfg.active(),
                cfg.createdAt(),
                cfg.updatedAt());
    }

    static MerchantWebhookDtos.WebhookDeliveryResponse toResp(WebhookDelivery d) {
        return new MerchantWebhookDtos.WebhookDeliveryResponse(
                d.id(),
                d.eventId(),
                d.eventType(),
                d.attemptNumber(),
                d.status().name(),
                d.httpStatus(),
                d.responseBody(),
                d.nextRetryAt(),
                d.createdAt(),
                d.deliveredAt());
    }
}
