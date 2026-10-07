package com.micropay.backend.infrastructure.security;

import com.micropay.backend.domain.valueobjects.UserId;

/**
 * Servicio de token firmado para verificación de email (one-time link).
 * JWT corto (10 min, claim "purpose": "verify_email" + user_id).
 * Producción: email que contiene el link con ?token=... al endpoint verify.
 *
 * Mismo servicio puede usarse para MFA verify-token, password-reset, etc.
 * con distintos purposes (claim typificado).
 */
public interface VerifiableTokenService {

    enum Purpose { VERIFY_EMAIL, MFA_SETUP_CONFIRM, PASSWORD_RESET, INVITE }

    /** Genera token JWT corto firmado con propósito. */
    String issueToken(Purpose p, UserId userId, long ttlMillis);

    /** Valida y parsea. Retorna empty si expirado/inválido. */
    java.util.Optional<UserId> verifyToken(Purpose expected, String tokenRaw);
}
