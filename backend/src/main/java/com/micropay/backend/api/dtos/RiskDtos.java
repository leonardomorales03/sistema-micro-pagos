package com.micropay.backend.api.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * DTOs del RiskEngine (T23 / Requisito 27).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class RiskDtos {

    @Schema(name = "VerifyChallengeRequest", description = "Código de verificación enviado al usuario (step-up)")
    public record VerifyChallengeRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "123456", description = "Código de 6 dígitos")
            @JsonProperty("code") @NotBlank String code
    ) {}

    @Schema(name = "VerifyChallengeResponse", description = "Resultado de la verificación del challenge")
    public record VerifyChallengeResponse(
            @JsonProperty("verified") boolean verified,
            @JsonProperty("token") String token
    ) {}

    @Schema(name = "RiskDecisionResponse", description = "Decisión de riesgo (diagnóstico)")
    public record RiskDecisionResponse(
            @JsonProperty("decision") String decision,
            @JsonProperty("reasons") List<String> reasons
    ) {}
}
