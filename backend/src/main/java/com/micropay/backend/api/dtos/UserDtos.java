package com.micropay.backend.api.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class UserDtos {

    public record RegisterRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "nuevo@ejemplo.com")
            @JsonProperty("email") String email,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Password en texto plano (se encripta bcrypt cost=12 server-side sobre HTTPS)")
            @JsonProperty("password") String password,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "María López")
            @JsonProperty("full_name") String fullName,
            @Schema(description = "Código referido (8 chars uppercase) — opcional")
            @JsonProperty("referral_code") String referralCode
    ) {}

    public record BlockRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Comportamiento sospechoso detectado")
            @JsonProperty("reason") String reason
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UserResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("email") String email,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("phone_number") String phoneNumber,
            @JsonProperty("kyc_level") String kycLevel,
            @JsonProperty("status") String status,
            @JsonProperty("email_verified") boolean emailVerified,
            @JsonProperty("referral_code") String referralCode,
            @JsonProperty("referred_by") UUID referredBy,
            @JsonProperty("roles") List<String> roles,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt
    ) {}
}
