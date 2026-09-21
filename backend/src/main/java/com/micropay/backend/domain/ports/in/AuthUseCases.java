package com.micropay.backend.domain.ports.in;

import com.micropay.backend.domain.entities.Session;
import com.micropay.backend.domain.valueobjects.Email;
import com.micropay.backend.domain.valueobjects.UserId;

/**
 * UseCases IN — Autenticación JWT + Refresh Token HttpOnly Cookie.
 */
public interface AuthUseCases {

    record AuthResult(String accessToken,
                      String refreshTokenRaw,
                      UserId userId,
                      String email,
                      java.util.List<String> roles) {}

    AuthResult login(Email email, String rawPassword, String deviceFingerprint, String userAgent, String ip);

    /** Refresh token: rotación obligatoria — token anterior se marca revoked. */
    AuthResult refresh(String refreshTokenRaw, String deviceFingerprint, String userAgent, String ip);

    /** Logout específico (sesión actual) — revoca token */
    void logout(String refreshTokenRaw);

    /** Cierra todas las sesiones del usuario (ej: password change, admin kick). */
    void logoutAllSessions(UserId userId, String reason, UserId byAdminId);

    Session getSessionDetails(String refreshTokenRaw);
}
