package com.micropay.backend.api.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "Auth")
public final class AuthDtos {

    public record LoginRequest(
            @Schema(description = "Email usuario", example = "usuario@ejemplo.com", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("email") String email,
            @Schema(description = "Password en texto plano (se compara bcrypt.matches server-side sobre HTTPS)", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("password") String password,
            @Schema(description = "Código TOTP MFA 6 dígitos (si MFA está habilitado; no requerido base)")
            @JsonProperty("mfa_code") String mfaCode,
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

    public record MfaSetupRequest(
            @JsonProperty("password") String currentPassword
    ) {}

    public record MfaVerifyRequest(
            @Schema(description = "Secreto base32 previamente generado en /setup")
            @JsonProperty("secret") String secretBase32,
            @Schema(description = "Código TOTP de 6 dígitos para confirmar activación")
            @JsonProperty("code") String code6digits
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MfaSetupResponse(
            @JsonProperty("secret_base32") String secretBase32,
            @JsonProperty("otp_auth_url") String otpAuthUrl,
            @JsonProperty("issuer") String issuer,
            @JsonProperty("account_name") String accountName
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
            @JsonProperty("roles") List<String> roles,
            @JsonProperty("mfa_required") Boolean mfaRequired,
            @JsonProperty("mfa_setup_needed") Boolean mfaSetupNeeded
    ) {}
}
