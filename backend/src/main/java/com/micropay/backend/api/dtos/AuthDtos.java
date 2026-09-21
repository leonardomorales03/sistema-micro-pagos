package com.micropay.backend.api.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTOs de la API v1 — TODOS Records Java 21 (inmutables).
 * Prefijo v1/: api/v1/*
 * <p>
 *   Documentación inline: @Schema (SpringDoc OpenAPI 3.1) + ejemplos.
 */
@Schema(name = "Auth")
public final class AuthDtos {

    public record LoginRequest(
            @Schema(description = "Email usuario", example = "usuario@ejemplo.com", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("email") String email,
            @Schema(description = "Password en texto plano (se compara bcrypt.matches server-side sobre HTTPS)", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("password") String password,
            @Schema(description = "Huella dispositivo (opcional para audit)")
            @JsonProperty("device_fingerprint") String deviceFingerprint,
            @Schema(description = "User-Agent para audit (se obtiene del header User-Agent si no se envía)")
            @JsonProperty("user_agent") String userAgent
    ) {}

    public record RefreshRequest(
            @Schema(description = "Valor cookie HttpOnly o body (no requerido si viene por cookie)")
            @JsonProperty("refresh_token") String refreshToken
    ) {}

    public record LogoutRequest(
            @JsonProperty("refresh_token") String refreshToken
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuthResponse(
            @Schema(description = "Access token JWT Bearer (15 min)", example = "eyJhbGciOiJIUzUxMiIs...")
            @JsonProperty("access_token") String accessToken,
            @Schema(description = "Duración access token en segundos", example = "900")
            @JsonProperty("expires_in") long expiresInSeconds,
            @Schema(description = "Tipo de token (siempre Bearer)")
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("user_id") UUID userId,
            @JsonProperty("email") String email,
            @JsonProperty("roles") List<String> roles
    ) {}
}
