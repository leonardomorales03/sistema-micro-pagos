package com.micropay.backend.domain.entities;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * AuditEvent — registro APPEND-ONLY de una acción crítica (Property 31).
 * <p>
 * Java puro (sin Spring/JPA). El adaptador de infraestructura lo persiste en
 * la tabla {@code audit_events} (V10). Nunca se actualiza ni se elimina.
 */
public record AuditEvent(
        UUID id,
        UUID actorUserId,
        String action,
        String entityType,
        UUID entityId,
        UUID correlationId,
        Map<String, Object> metadata,
        String ipAddress,
        String userAgent,
        Instant createdAt
) {

    /** Factoría tolerante: genera {@code id} y {@code createdAt} si llegan null. */
    public static AuditEvent of(UUID id,
                                UUID actorUserId,
                                String action,
                                String entityType,
                                UUID entityId,
                                UUID correlationId,
                                Map<String, Object> metadata,
                                String ipAddress,
                                String userAgent,
                                Instant createdAt) {
        return new AuditEvent(
                id != null ? id : UUID.randomUUID(),
                actorUserId,
                action,
                entityType,
                entityId,
                correlationId,
                metadata != null ? metadata : Map.of(),
                ipAddress,
                userAgent,
                createdAt != null ? createdAt : Instant.now()
        );
    }
}
