package com.micropay.backend.infrastructure.security;

import com.micropay.backend.domain.valueobjects.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Servicio JWT abstracto (port infra). Genera y valida JWT tokens.
 *   — Access token: Bearer JWT corta duración (15m)
 *   — Refresh token: random UUID (no JWT) largo plazo (7 días), almacenado hash en DB
 *
 * Implementación concreta: [JjwtJwtService.java]
 */
public interface JwtService {

    record ParsedToken(UserId userId, String subject, List<String> roles, Instant expiresAt, Map<String,Object> claims) {}

    /** Genera access token JWT (HS512 o RS256 según config). */
    String generateAccessToken(UserId userId, String email, List<String> roles);

    /** Genera refresh token raw (random hex) — NO JWT para poder revocarlo fácil por DB. */
    String generateRefreshTokenRaw();

    /** SHA256 hex del refresh raw — se almacena así en tabla sessions por seguridad (no guardamos raw) */
    String hashRefreshToken(String refreshTokenRaw);

    /** Parsea y valida access token JWT. Retorna empty si expirado/firma inválida. */
    ParsedToken parseAccessToken(String jwt);

    /** Duración access token en ms (para T8 SecurityConfig). */
    long accessTokenTtlMillis();

    /** Duración refresh token en ms (para T8 cookie maxAge). */
    long refreshTokenTtlMillis();

    /** Cookie name refresh token (HttpOnly/SameSite). */
    String refreshCookieName();

    /** Issuer para validar "iss" claim en parse. */
    String issuer();
}
