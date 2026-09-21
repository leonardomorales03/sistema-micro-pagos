package com.micropay.backend.domain.entities;

import com.micropay.backend.domain.valueobjects.UserId;

import java.time.Instant;
import java.util.UUID;

/**
 * Session — Refresh token HttpOnly session.
 * Almacenamos refresh tokens en DB con user_id y device fingerprint,
 * para poder revocar (logout específico o todas las sesiones).
 */
public final class Session {

    private final UUID id;
    private final UserId userId;
    private final String refreshTokenHash;   // hash SHA256 — nunca almacenamos token raw
    private final String deviceFingerprint; // ip + ua hash
    private final String userAgent;
    private final String ipAddress;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private Instant revokedAt;
    private String revokedReason; // LOGOUT / ADMIN_KICK / PASSWORD_CHANGE
    private boolean rotated;

    public Session(UUID id,
                   UserId userId,
                   String refreshTokenHash,
                   String deviceFingerprint,
                   String userAgent,
                   String ipAddress,
                   Instant issuedAt,
                   Instant expiresAt) {
        this.id = id == null ? UUID.randomUUID() : id;
        if (userId == null) throw new IllegalArgumentException("userId");
        if (refreshTokenHash == null || refreshTokenHash.length() < 32)
            throw new IllegalArgumentException("refreshTokenHash");
        this.userId = userId;
        this.refreshTokenHash = refreshTokenHash;
        this.deviceFingerprint = deviceFingerprint;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
        this.issuedAt = issuedAt == null ? Instant.now() : issuedAt;
        this.expiresAt = expiresAt;
        this.rotated = false;
    }

    public void revoke(String reason) {
        if (this.revokedAt != null) return; // idempotente
        this.revokedAt = Instant.now();
        this.revokedReason = reason;
    }

    public void markRotated() { this.rotated = true; }

    public boolean isActive() {
        return this.revokedAt == null && Instant.now().isBefore(this.expiresAt);
    }

    public boolean matchesToken(String otherTokenHash) {
        return this.refreshTokenHash.equals(otherTokenHash);
    }

    // ────────── GETTERS ──────────
    public UUID id() { return id; }
    public UserId userId() { return userId; }
    public String refreshTokenHash() { return refreshTokenHash; }
    public String deviceFingerprint() { return deviceFingerprint; }
    public String userAgent() { return userAgent; }
    public String ipAddress() { return ipAddress; }
    public Instant issuedAt() { return issuedAt; }
    public Instant expiresAt() { return expiresAt; }
    public Instant revokedAt() { return revokedAt; }
    public String revokedReason() { return revokedReason; }
    public boolean rotated() { return rotated; }
}
