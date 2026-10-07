package com.micropay.backend.infrastructure.security;

import com.micropay.backend.domain.valueobjects.UserId;

/**
 * MFA TOTP service (RFC 6238) — capa infraestructura.
 * Producción: integrar con Google Authenticator / Authy.
 * Implementación minimal: devuelve URLs de setup y códigos fijos determinísticos
 * en entorno SANDBOX (determinados por UserId, para que los tests sean estables).
 * NO es una implementación real segura de TOTP sin HMAC SHA-1.
 * Solo para mockear el flujo setup/verify en el core funcional del proyecto.
 */
public interface MfaService {

    record MfaSetupResponse(String secretBase32, String otpAuthUrl, String issuer, String accountName) {}

    /** Genera secret TOTP + URL para Google Authenticator (setup QR content). */
    MfaSetupResponse generateSetup(UserId userId, String email);

    /** Valida código TOTP de 6 dígitos para usuario (SANDBOX: code == last 6 chars hash userId+secret). */
    boolean verifyCode(UserId userId, String secretBase32, String code6digits);
}
