package com.micropay.backend.infrastructure.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micropay.backend.domain.entities.AuditEvent;
import com.micropay.backend.domain.ports.out.AuditEventRepository;
import com.micropay.backend.infrastructure.persistence.jpa.entities.AuditEventJpa;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.AuditEventJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador Postgres del puerto {@link AuditEventRepository} (tabla {@code audit_events}, V10).
 * APPEND-ONLY: {@link #record(AuditEvent)} sólo hace INSERT; no existe update/delete.
 */
@Repository
public class PostgresAuditEventRepository implements AuditEventRepository {

    private static final Logger log = LoggerFactory.getLogger(PostgresAuditEventRepository.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AuditEventJpaRepository jpa;

    public PostgresAuditEventRepository(AuditEventJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void record(AuditEvent e) {
        jpa.save(toJpa(e));
    }

    @Override
    public List<AuditEvent> list(String entityType, UUID entityId, UUID actorUserId, int page, int pageSize) {
        int p = Math.max(0, page);
        int size = Math.min(200, Math.max(1, pageSize));
        return jpa.search(entityType, entityId, actorUserId, PageRequest.of(p, size))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    // ──────────────────────── Mappers Domain ↔ JPA ────────────────────────

    private AuditEventJpa toJpa(AuditEvent d) {
        AuditEventJpa j = new AuditEventJpa();
        j.setId(d.id());
        j.setActorUserId(d.actorUserId());
        j.setAction(d.action());
        j.setEntityType(d.entityType());
        j.setEntityId(d.entityId());
        j.setCorrelationId(d.correlationId());
        j.setMetadata(toJson(d.metadata()));
        j.setIpAddress(toInet(d.ipAddress()));
        j.setUserAgent(d.userAgent());
        j.setCreatedAt(d.createdAt() != null ? d.createdAt() : java.time.Instant.now());
        return j;
    }

    private AuditEvent toDomain(AuditEventJpa j) {
        if (j == null) return null;
        return new AuditEvent(
                j.getId(),
                j.getActorUserId(),
                j.getAction(),
                j.getEntityType(),
                j.getEntityId(),
                j.getCorrelationId(),
                fromJson(j.getMetadata()),
                j.getIpAddress() != null ? j.getIpAddress().getHostAddress() : null,
                j.getUserAgent(),
                j.getCreatedAt()
        );
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return MAPPER.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (Exception e) {
            log.warn("No se pudo serializar metadata de audit event: {}", e.getMessage());
            return "{}";
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("No se pudo deserializar metadata de audit event: {}", e.getMessage());
            return Map.of();
        }
    }

    private InetAddress toInet(String ip) {
        if (ip == null || ip.isBlank()) return null;
        try {
            return InetAddress.getByName(ip);
        } catch (Exception e) {
            log.warn("IP de audit event inválida '{}': {}", ip, e.getMessage());
            return null;
        }
    }
}
