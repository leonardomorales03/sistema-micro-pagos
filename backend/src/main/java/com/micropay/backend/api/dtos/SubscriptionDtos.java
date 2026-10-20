package com.micropay.backend.api.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTOs de Subscriptions y ciclo de facturación (Requirement 22.2, 22.3, 22.6) — T20.
 */
public final class SubscriptionDtos {

    /** Payload de suscripción (aprueba el mandato de cobro recurrente sobre la wallet indicada). */
    public record SubscribeRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Plan al que se suscribe")
            @JsonProperty("plan_id") UUID planId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Wallet del suscriptor de la que se debitará")
            @JsonProperty("wallet_id") UUID walletId
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "SubscriptionResponse", description = "Suscripción activa del usuario")
    public record SubscriptionResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("plan_id") UUID planId,
            @JsonProperty("subscriber_user_id") UUID subscriberUserId,
            @JsonProperty("subscriber_wallet_id") UUID subscriberWalletId,
            @JsonProperty("status") String status,
            @JsonProperty("current_period_start") Instant currentPeriodStart,
            @JsonProperty("next_billing_date") Instant nextBillingDate,
            @JsonProperty("canceled_at") Instant canceledAt,
            @JsonProperty("dunning_attempts") int dunningAttempts,
            @JsonProperty("last_billing_result") String lastBillingResult,
            @JsonProperty("created_at") Instant createdAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "SubscriptionListResponse", description = "Listado paginado de suscripciones")
    public record SubscriptionListResponse(
            @JsonProperty("data") List<SubscriptionResponse> data,
            @JsonProperty("page") int page,
            @JsonProperty("page_size") int pageSize
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "BillingRunResponse", description = "Resumen de una ejecución del ciclo de facturación")
    public record BillingRunResponse(
            @JsonProperty("scanned") int scanned,
            @JsonProperty("succeeded") int succeeded,
            @JsonProperty("failed") int failed,
            @JsonProperty("canceled") int canceled
    ) {}
}
