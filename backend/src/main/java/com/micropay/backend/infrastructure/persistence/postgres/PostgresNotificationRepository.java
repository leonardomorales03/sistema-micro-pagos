package com.micropay.backend.infrastructure.persistence.postgres;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micropay.backend.domain.entities.UserNotification;
import com.micropay.backend.domain.ports.out.NotificationRepository;
import com.micropay.backend.domain.valueobjects.NotificationChannel;
import com.micropay.backend.domain.valueobjects.NotificationStatus;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.infrastructure.persistence.jpa.entities.UserNotificationJpa;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.UserNotificationJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador Postgres del puerto {@link NotificationRepository} (tabla `user_notifications`, V8).
 * El payload viaja como JSONB ↔ Map en el dominio.
 */
@Repository
public class PostgresNotificationRepository implements NotificationRepository {

    private static final Logger log = LoggerFactory.getLogger(PostgresNotificationRepository.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final UserNotificationJpaRepository jpa;
    private final ObjectMapper json;

    public PostgresNotificationRepository(UserNotificationJpaRepository jpa, ObjectMapper json) {
        this.jpa = jpa;
        this.json = json;
    }

    @Override
    public UserNotification save(UserNotification n) {
        return toDomain(jpa.save(toJpa(n)));
    }

    @Override
    public Optional<UserNotification> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public List<UserNotification> listByUser(UserId userId, int page, int pageSize) {
        Pageable p = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, pageSize)));
        Page<UserNotificationJpa> rs = jpa.findByUserIdOrderByCreatedAtDesc(userId.uuid(), p);
        return rs.getContent().stream().map(this::toDomain).toList();
    }

    @Override
    public long countUnread(UserId userId) {
        return jpa.countByUserIdAndReadAtIsNull(userId.uuid());
    }

    @Override
    public int markAllRead(UserId userId) {
        return jpa.markAllRead(userId.uuid(), UserNotificationJpa.NotificationStatusJpa.READ, Instant.now());
    }

    // ──────────────────────── Mappers Domain ↔ JPA ────────────────────────

    private UserNotificationJpa toJpa(UserNotification n) {
        UserNotificationJpa j = new UserNotificationJpa();
        j.setId(n.id());
        j.setUserId(n.userId().uuid());
        j.setEventType(n.eventType());
        j.setPayload(writePayload(n.payload()));
        j.setChannel(UserNotificationJpa.NotificationChannelJpa.valueOf(n.channel().name()));
        j.setStatus(UserNotificationJpa.NotificationStatusJpa.valueOf(n.status().name()));
        j.setCorrelationId(n.correlationId());
        j.setCreatedAt(n.createdAt());
        j.setReadAt(n.readAt());
        return j;
    }

    private UserNotification toDomain(UserNotificationJpa j) {
        if (j == null) return null;
        return UserNotification.builder()
                .id(j.getId())
                .userId(UserId.from(j.getUserId()))
                .eventType(j.getEventType())
                .payload(readPayload(j.getPayload()))
                .channel(NotificationChannel.valueOf(j.getChannel().name()))
                .status(NotificationStatus.valueOf(j.getStatus().name()))
                .correlationId(j.getCorrelationId())
                .createdAt(j.getCreatedAt())
                .readAt(j.getReadAt())
                .build();
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return json.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (Exception e) {
            log.warn("No se pudo serializar payload de notificación: {}", e.getMessage());
            return "{}";
        }
    }

    private Map<String, Object> readPayload(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        try {
            return json.readValue(raw, MAP_TYPE);
        } catch (Exception e) {
            log.warn("Payload de notificación ilegible, se ignora: {}", e.getMessage());
            return Map.of();
        }
    }
}
