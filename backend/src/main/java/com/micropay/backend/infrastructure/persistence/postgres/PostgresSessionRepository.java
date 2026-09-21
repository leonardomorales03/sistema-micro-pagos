package com.micropay.backend.infrastructure.persistence.postgres;

import com.micropay.backend.domain.entities.Session;
import com.micropay.backend.domain.ports.out.SessionRepository;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.infrastructure.persistence.jpa.entities.SessionJpa;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.SessionJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PostgresSessionRepository implements SessionRepository {

    private final SessionJpaRepository jpa;

    public PostgresSessionRepository(SessionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Session save(Session session) {
        SessionJpa saved = jpa.save(toJpa(session));
        return toDomain(saved);
    }

    @Override
    public Optional<Session> findByRefreshTokenHash(String refreshTokenHash) {
        return jpa.findByRefreshTokenHash(refreshTokenHash).map(this::toDomain);
    }

    @Override
    public Optional<Session> findById(UUID sessionId) {
        return jpa.findById(sessionId).map(this::toDomain);
    }

    @Override
    public List<Session> listByUser(UserId userId) {
        return jpa.findByUserIdOrderByIssuedAtDesc(userId.uuid()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public int revokeAllForUser(UserId userId, String reason) {
        return jpa.revokeAllActiveForUser(userId.uuid(), reason, Instant.now());
    }

    // ──────────────── MAPPERS ────────────────

    Session toDomain(SessionJpa j) {
        if (j == null) return null;
        Session s = new Session(
                j.getId(),
                UserId.from(j.getUserId()),
                j.getRefreshTokenHash(),
                j.getDeviceFingerprint(),
                j.getUserAgent(),
                j.getIpAddress() != null ? j.getIpAddress().getHostAddress() : null,
                j.getIssuedAt(),
                j.getExpiresAt()
        );
        if (j.getRevokedAt() != null) {
            // Cargamos estado revoked via reflection: Session.revoke() setea reason+now
            try {
                setField(s, "revokedAt", j.getRevokedAt());
                setField(s, "revokedReason", j.getRevokedReason());
            } catch (Exception ignored) {}
        }
        if (j.isRotated()) s.markRotated();
        return s;
    }

    SessionJpa toJpa(Session d) {
        if (d == null) return null;
        SessionJpa j = new SessionJpa();
        j.setId(d.id());
        j.setUserId(d.userId().uuid());
        j.setRefreshTokenHash(d.refreshTokenHash());
        j.setDeviceFingerprint(d.deviceFingerprint());
        j.setUserAgent(d.userAgent());
        j.setIpAddress(toInet(d.ipAddress()));
        j.setIssuedAt(d.issuedAt());
        j.setExpiresAt(d.expiresAt());
        j.setRevokedAt(d.revokedAt());
        j.setRevokedReason(d.revokedReason());
        j.setRotated(d.rotated());
        if (j.getCreatedAt() == null) j.setCreatedAt(Instant.now());
        return j;
    }

    private static InetAddress toInet(String ip) {
        if (ip == null || ip.isBlank()) return null;
        try { return InetAddress.getByName(ip); }
        catch (UnknownHostException e) { return null; }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo settear " + name + " en Session", e);
        }
    }
}
