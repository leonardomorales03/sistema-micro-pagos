package com.micropay.backend.infrastructure.security;

import com.micropay.backend.domain.valueobjects.UserId;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

/**
 * Implementación concreta JwtService con JJWT 0.12.x (io.jsonwebtoken).
 * <p>
 *   Algoritmo: HS512 (HMAC-SHA512) con clave 64-byte. En PROD: reemplazar por RS256 con par de claves.
 *   Secret key tomado de application.yml micropay.jwt.secret-key (variable SPRING_JWT_SECRET_KEY).
 *   Si NO hay secret-key configurada, genera una efímera y loggea WARN (desarrollo local).
 */
@Component
public class JjwtJwtService implements JwtService {

    private static final Logger log = LoggerFactory.getLogger(JjwtJwtService.class);
    private static final String CLAIM_ROLES = "roles";
    private static final SecureRandom RND = new SecureRandom();
    private static final int REFRESH_BYTES = 48; // = 96 hex chars, cabe en column 96 de sessions

    private final SecretKey signKey;
    private final long accessTtlMs;
    private final long refreshTtlMs;
    private final String refreshCookieName;
    private final String issuer;

    public JjwtJwtService(
            @Value("${micropay.jwt.secret-key:}") String secretKey,
            @Value("${micropay.jwt.access-token-expiration-ms:900000}") long accessTtlMs,
            @Value("${micropay.jwt.refresh-token-expiration-ms:604800000}") long refreshTtlMs,
            @Value("${micropay.jwt.refresh-token-cookie-name:MICROPAY_REFRESH_TOKEN}") String refreshCookieName,
            @Value("${micropay.jwt.issuer:micropay-backend}") String issuer
    ) {
        this.accessTtlMs = accessTtlMs;
        this.refreshTtlMs = refreshTtlMs;
        this.refreshCookieName = refreshCookieName;
        this.issuer = issuer;

        SecretKey resolved;
        if (secretKey == null || secretKey.isBlank()) {
            byte[] keyBytes = new byte[64];
            RND.nextBytes(keyBytes);
            resolved = Keys.hmacShaKeyFor(keyBytes);
            log.warn("⚠️  MICROPAY_JWT_SECRET_KEY no configurada. Se generó clave efímera en memoria. " +
                    "¡NO USAR EN PRODUCCIÓN! (Los tokens expiran al reiniciar el proceso)");
        } else {
            byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < 32) {
                byte[] padded = Arrays.copyOf(keyBytes, 64);
                resolved = Keys.hmacShaKeyFor(padded);
                log.warn("⚠️  Longitud secret-key < 32 bytes; completada con padding a 64 bytes (HS512).");
            } else {
                resolved = Keys.hmacShaKeyFor(keyBytes);
            }
        }
        this.signKey = resolved;
    }

    @Override
    public String generateAccessToken(UserId userId, String email, List<String> roles) {
        Instant now = Instant.now();
        Instant exp = now.plusMillis(accessTtlMs);
        return Jwts.builder()
                .header().add("typ", "JWT").add("alg", "HS512").and()
                .subject(userId.uuid().toString())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .id(UUID.randomUUID().toString())
                .claim("email", email)
                .claim(CLAIM_ROLES, List.copyOf(roles != null ? roles : List.of("ROLE_USER")))
                .signWith(signKey, Jwts.SIG.HS512)
                .compact();
    }

    @Override
    public String generateRefreshTokenRaw() {
        byte[] b = new byte[REFRESH_BYTES];
        RND.nextBytes(b);
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    @Override
    public String hashRefreshToken(String refreshTokenRaw) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] hash = d.digest(refreshTokenRaw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    @Override
    public ParsedToken parseAccessToken(String jwt) {
        if (jwt == null || jwt.isBlank()) return null;
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(signKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(jwt);
            Claims c = jws.getPayload();
            UUID sub = UUID.fromString(c.getSubject());
            Object rawRoles = c.get(CLAIM_ROLES);
            @SuppressWarnings("unchecked")
            List<String> roles = rawRoles instanceof List<?>
                    ? ((List<Object>) rawRoles).stream().map(String::valueOf).toList()
                    : List.of("ROLE_USER");
            String email = c.get("email", String.class);
            return new ParsedToken(UserId.from(sub), email, roles, c.getExpiration().toInstant(), c);
        } catch (ExpiredJwtException e) {
            log.debug("JWT expirado: {}", e.getMessage());
            return null;
        } catch (UnsupportedJwtException | MalformedJwtException | SignatureException
                 | IllegalArgumentException | NullPointerException e) {
            log.debug("JWT inválido: {}", e.getMessage());
            return null;
        }
    }

    @Override public long accessTokenTtlMillis() { return accessTtlMs; }
    @Override public long refreshTokenTtlMillis() { return refreshTtlMs; }
    @Override public String refreshCookieName() { return refreshCookieName; }
    @Override public String issuer() { return issuer; }
}
