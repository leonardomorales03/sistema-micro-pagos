package com.micropay.backend.application.services;

import com.micropay.backend.domain.entities.Session;
import com.micropay.backend.domain.entities.User;
import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.exceptions.UserNotFoundException;
import com.micropay.backend.domain.ports.in.AuthUseCases;
import com.micropay.backend.domain.ports.out.SessionRepository;
import com.micropay.backend.domain.ports.out.UserRepository;
import com.micropay.backend.domain.valueobjects.Email;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.infrastructure.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class AuthService implements AuthUseCases {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final SessionRepository sessions;
    private final JwtService jwt;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository users, SessionRepository sessions, JwtService jwt, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.sessions = sessions;
        this.jwt = jwt;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public AuthResult login(Email email,
                            String rawPassword,
                            String deviceFingerprint,
                            String userAgent,
                            String ip) {
        if (email == null || rawPassword == null) {
            throw new IllegalArgumentException("email y password son requeridos");
        }
        User user = users.findByEmail(email)
                .orElseThrow(() -> new BusinessRuleViolationException("INVALID_CREDENTIALS",
                        "Credenciales inválidas (email o password)"));

        if (user.status() != User.Status.ACTIVE && user.status() != User.Status.PENDING_EMAIL_VERIFICATION) {
            throw new BusinessRuleViolationException("USER_NOT_ACTIVE",
                    "Usuario en estado %s no puede iniciar sesión".formatted(user.status()));
        }

        String storedHash = readStoredPasswordHash(user);
        if (!passwordEncoder.matches(rawPassword, storedHash)) {
            throw new BusinessRuleViolationException("INVALID_CREDENTIALS",
                    "Credenciales inválidas (email o password)");
        }

        // 3. Crear refresh token + access token
        String refreshRaw = jwt.generateRefreshTokenRaw();
        String hash = jwt.hashRefreshToken(refreshRaw);
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusMillis(jwt.refreshTokenTtlMillis());
        Session session = new Session(
                null, user.id(), hash,
                deviceFingerprint, userAgent, ip, issuedAt, expiresAt
        );
        Session saved = sessions.save(session);

        String access = jwt.generateAccessToken(user.id(), user.email().value(), user.roles());
        log.info("✅ Login OK user={} sessionId={}", user.id(), saved.id());
        return new AuthResult(access, refreshRaw, user.id(), user.email().value(), user.roles());
    }

    @Override
    @Transactional
    public AuthResult refresh(String refreshTokenRaw,
                               String deviceFingerprint,
                               String userAgent,
                               String ip) {
        if (refreshTokenRaw == null || refreshTokenRaw.isBlank()) {
            throw new BusinessRuleViolationException("REFRESH_MISSING", "Falta refresh token");
        }
        String hash = jwt.hashRefreshToken(refreshTokenRaw);
        Session session = sessions.findByRefreshTokenHash(hash)
                .orElseThrow(() -> new BusinessRuleViolationException("REFRESH_INVALID",
                        "Refresh token inválido o revocado"));
        if (!session.isActive()) {
            throw new BusinessRuleViolationException("REFRESH_EXPIRED_OR_REVOKED",
                    "Refresh token expirado o revocado (status=%s, revoked=%s)"
                            .formatted(session.isActive(), session.revokedAt() != null));
        }

        User user = users.findById(session.userId())
                .orElseThrow(() -> new UserNotFoundException(session.userId().uuid()));
        if (user.status() == User.Status.BLOCKED || user.status() == User.Status.DELETED) {
            throw new BusinessRuleViolationException("USER_BLOCKED",
                    "Usuario bloqueado, no se puede renovar token");
        }

        // Rotación: marcar token anterior como rotated/revocado y generar uno nuevo
        session.markRotated();
        session.revoke("REFRESH_ROTATED");
        sessions.save(session);

        String newRefreshRaw = jwt.generateRefreshTokenRaw();
        String newHash = jwt.hashRefreshToken(newRefreshRaw);
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusMillis(jwt.refreshTokenTtlMillis());
        Session newSession = new Session(
                null, user.id(), newHash,
                deviceFingerprint, userAgent, ip, issuedAt, expiresAt
        );
        sessions.save(newSession);

        String access = jwt.generateAccessToken(user.id(), user.email().value(), user.roles());
        log.debug("🔄 Refresh rotado user={}", user.id());
        return new AuthResult(access, newRefreshRaw, user.id(), user.email().value(), user.roles());
    }

    @Override
    @Transactional
    public void logout(String refreshTokenRaw) {
        if (refreshTokenRaw == null) return;
        String hash = jwt.hashRefreshToken(refreshTokenRaw);
        Optional<Session> opt = sessions.findByRefreshTokenHash(hash);
        opt.ifPresent(s -> {
            s.revoke("LOGOUT");
            sessions.save(s);
        });
    }

    @Override
    @Transactional
    public void logoutAllSessions(UserId userId, String reason, UserId byAdminId) {
        sessions.revokeAllForUser(userId, reason != null ? reason : "ADMIN_KICK");
    }

    @Override
    public Session getSessionDetails(String refreshTokenRaw) {
        String hash = jwt.hashRefreshToken(refreshTokenRaw);
        return sessions.findByRefreshTokenHash(hash).orElse(null);
    }

    private static String readStoredPasswordHash(User user) {
        try {
            java.lang.reflect.Field f = User.class.getDeclaredField("passwordHash");
            f.setAccessible(true);
            return (String) f.get(user);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo leer passwordHash de User", e);
        }
    }
}
