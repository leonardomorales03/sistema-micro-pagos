package com.micropay.backend.api.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTOs de Planes de suscripción (Requirement 22.1) — T20.
 */
public final class PlanDtos {

    /** Payload de creación de Plan (comercio). */
    public record CreatePlanRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Plan Básico", description = "Nombre del plan (máx 255)")
            @JsonProperty("name") String name,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "19900.00", description = "Monto > 0")
            @JsonProperty("amount") BigDecimal amount,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "COP", allowableValues = {"COP","USD","EUR"})
            @JsonProperty("currency") String currency,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "MONTH", allowableValues = {"DAY","WEEK","MONTH","YEAR"},
                    description = "Intervalo de facturación")
            @JsonProperty("interval") String interval,

            @Schema(example = "1", description = "Cada cuántos intervalos se cobra (>= 1, por defecto 1)")
            @JsonProperty("interval_count") Integer intervalCount,

            @Schema(example = "7", description = "Días de prueba gratuita (>= 0, opcional)")
            @JsonProperty("trial_days") Integer trialDays
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "PlanResponse", description = "Plan de suscripción")
    public record PlanResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("merchant_user_id") UUID merchantUserId,
            @JsonProperty("name") String name,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency") String currency,
            @JsonProperty("interval") String interval,
            @JsonProperty("interval_count") int intervalCount,
            @JsonProperty("trial_days") Integer trialDays,
            @JsonProperty("status") String status,
            @JsonProperty("created_at") Instant createdAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "PlanListResponse", description = "Listado paginado de planes")
    public record PlanListResponse(
            @JsonProperty("data") List<PlanResponse> data,
            @JsonProperty("page") int page,
            @JsonProperty("page_size") int pageSize
    ) {}
}
