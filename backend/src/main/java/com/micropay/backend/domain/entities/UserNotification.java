package com.micropay.backend.domain.entities;

import com.micropay.backend.domain.valueobjects.NotificationChannel;
import com.micropay.backend.domain.valueobjects.NotificationStatus;
import com.micropay.backend.domain.valueobjects.UserId;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * UserNotification — entrada del log append-only de notificaciones de un usuario (Requisito 25.3).
 *
 * INVARIANTES:
 *   BR#01: user_id y event_type son obligatorios.
 *   BR#02: readAt sólo puede fijarse una vez (idempotente); estado asociado READ.
 *   BR#03: el payload es inmutable una vez creada la notificación (append-only).
 */
public final class UserNotification {

    private final UUID id;
    private final UserId userId;
    private final String eventType;
    private final Map<String, Object> payload;
    private final NotificationChannel channel;
    private final UUID correlationId;
    private final Instant createdAt;
    private NotificationStatus status;
    private Instant readAt;

    private UserNotification(Builder b) {
        if (b.userId == null) throw new IllegalArgumentException("userId es requerido");
        if (b.eventType == null || b.eventType.isBlank()) {
            throw new IllegalArgumentException("eventType es requerido");
        }
        this.id = b.id == null ? UUID.randomUUID() : b.id;
        this.userId = b.userId;
        this.eventType = b.eventType;
        this.payload = b.payload == null ? Map.of() : Map.copyOf(b.payload);
        this.channel = b.channel == null ? NotificationChannel.IN_APP : b.channel;
        this.status = b.status == null ? NotificationStatus.SENT : b.status;
        this.correlationId = b.correlationId;
        this.createdAt = b.createdAt == null ? Instant.now() : b.createdAt;
        this.readAt = b.readAt;
        if (this.readAt != null) this.status = NotificationStatus.READ;
    }

    public static Builder builder() { return new Builder(); }

    /** Crea una notificación ya entregada al canal (estado SENT). */
    public static UserNotification sent(UserId userId,
                                        String eventType,
                                        NotificationChannel channel,
                                        Map<String, Object> payload) {
        return UserNotification.builder()
                .userId(userId)
                .eventType(eventType)
                .channel(channel)
                .payload(payload)
                .status(NotificationStatus.SENT)
                .build();
    }

    // ────────── Comportamiento ──────────

    /** Marca como leída (idempotente). */
    public void markRead() {
        if (this.readAt != null) return;
        this.readAt = Instant.now();
        this.status = NotificationStatus.READ;
    }

    public boolean isRead() { return readAt != null; }

    // ────────── Getters ──────────

    public UUID id() { return id; }
    public UserId userId() { return userId; }
    public String eventType() { return eventType; }
    public Map<String, Object> payload() { return payload; }
    public NotificationChannel channel() { return channel; }
    public NotificationStatus status() { return status; }
    public UUID correlationId() { return correlationId; }
    public Instant createdAt() { return createdAt; }
    public Instant readAt() { return readAt; }

    // ────────── Builder ──────────

    public static final class Builder {
        private UUID id;
        private UserId userId;
        private String eventType;
        private Map<String, Object> payload;
        private NotificationChannel channel;
        private NotificationStatus status;
        private UUID correlationId;
        private Instant createdAt;
        private Instant readAt;

        public Builder id(UUID v) { this.id = v; return this; }
        public Builder userId(UserId v) { this.userId = v; return this; }
        public Builder eventType(String v) { this.eventType = v; return this; }
        public Builder payload(Map<String, Object> v) { this.payload = v; return this; }
        public Builder channel(NotificationChannel v) { this.channel = v; return this; }
        public Builder status(NotificationStatus v) { this.status = v; return this; }
        public Builder correlationId(UUID v) { this.correlationId = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder readAt(Instant v) { this.readAt = v; return this; }

        public UserNotification build() { return new UserNotification(this); }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof UserNotification other && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
