package com.micropay.backend.api.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTOs de webhooks a comercios (Requisito 25.4, 25.5) — T22.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class MerchantWebhookDtos {

    @Schema(name = "WebhookConfigRequest", description = "Alta/actualización de la configuración de webhook del comercio")
    public record WebhookConfigRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "https://mi-tienda.com/webhooks/micropay")
            @JsonProperty("endpoint_url") @NotBlank String endpointUrl,

            @Schema(description = "Eventos suscritos; omitir o [\"*\"] para todos")
            @JsonProperty("subscribed_events") List<String> subscribedEvents,

            @Schema(description = "Si true, rota el signing_secret (invalida firmas anteriores)")
            @JsonProperty("rotate_secret") Boolean rotateSecret
    ) {}

    @Schema(name = "WebhookConfigResponse", description = "Configuración vigente del comercio")
    public record WebhookConfigResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("endpoint_url") String endpointUrl,
            @JsonProperty("signing_secret") String signingSecret,
            @JsonProperty("subscribed_events") List<String> subscribedEvents,
            @JsonProperty("active") boolean active,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt
    ) {}

    @Schema(name = "WebhookDeliveryResponse", description = "Intento de entrega de un evento al comercio")
    public record WebhookDeliveryResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("event_id") UUID eventId,
            @JsonProperty("event_type") String eventType,
            @JsonProperty("attempt_number") int attemptNumber,
            @JsonProperty("status") String status,
            @JsonProperty("http_status") Integer httpStatus,
            @JsonProperty("response_body") String responseBody,
            @JsonProperty("next_retry_at") Instant nextRetryAt,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("delivered_at") Instant deliveredAt
    ) {}

    @Schema(name = "WebhookDeliveryListResponse", description = "Historial paginado de entregas")
    public record WebhookDeliveryListResponse(
            @JsonProperty("data") List<WebhookDeliveryResponse> data,
            @JsonProperty("page") int page,
            @JsonProperty("page_size") int pageSize
    ) {}

    @Schema(name = "WebhookSimulateRequest", description = "Evento de prueba a disparar al endpoint propio")
    public record WebhookSimulateRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "PAYMENT_REQUEST_PAID")
            @JsonProperty("event_type") @NotBlank String eventType
    ) {}
}
