package com.micropay.backend.infrastructure.security;

import com.micropay.backend.domain.valueobjects.UserId;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementación JJWT (reusando sign key de JwtService si disponible; o fallback propia).
 * Objetivo: token short-lived purpose VERIFY_EMAIL.
 */
@Component
public class JjwtVerifiableTokenService implements VerifiableTokenService {

    private static final Logger log = LoggerFactory.getLogger(JjwtVerifiableTokenService.class);
    private static final String CLAIM_PURPOSE = "purpose";
    private static final long DEFAULT_TTL = 10 * 60 * 1000L; // 10 minutos

    private final SecretKey signKey;
    private final String issuer;

    public JjwtVerifiableTokenService(
            @Value("${micropay.jwt.secret-key:}") String secretKey,
            @Value("${micropay.jwt.issuer:micropay-backend}") String issuer
    ) {
        this.issuer = issuer;
        byte[] k = (secretKey == null || secretKey.isBlank() ? UUID.randomUUID().toString() : secretKey)
                .getBytes(StandardCharsets.UTF_8);
        if (k.length < 32) {
            byte[] padded = new byte[64];
            System.arraycopy(k, 0, padded, 0, k.length);
            k = padded;
        }
        this.signKey = Keys.hmacShaKeyFor(k);
    }

    @Override
    public String issueToken(Purpose p, UserId userId, long ttlMillis) {
        long ttl = ttlMillis > 0 ? ttlMillis : DEFAULT_TTL;
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.uuid().toString())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(ttl)))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_PURPOSE, p.name())
                .signWith(signKey, Jwts.SIG.HS512)
                .compact();
    }

    @Override
    public Optional<UserId> verifyToken(Purpose expected, String tokenRaw) {
        if (tokenRaw == null || tokenRaw.isBlank()) return Optional.empty();
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(signKey)
                    .requireIssuer(issuer)
                    .require(CLAIM_PURPOSE, expected.name())
                    .build()
                    .parseSignedClaims(tokenRaw);
            UUID id = UUID.fromString(jws.getPayload().getSubject());
            return Optional.of(UserId.from(id));
        } catch (Exception e) {
            log.debug("Token inválido/expirado {}: {}", expected, e.getMessage());
            return Optional.empty();
        }
    }
}
