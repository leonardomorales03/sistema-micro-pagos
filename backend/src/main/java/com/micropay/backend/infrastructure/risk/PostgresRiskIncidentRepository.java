package com.micropay.backend.infrastructure.risk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micropay.backend.domain.entities.RiskIncident;
import com.micropay.backend.domain.ports.out.RiskIncidentRepository;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.infrastructure.persistence.jpa.entities.RiskIncidentJpa;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.RiskIncidentJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador Postgres del puerto {@link RiskIncidentRepository} (tabla {@code risk_incidents}, V1).
 */
@Repository
public class PostgresRiskIncidentRepository implements RiskIncidentRepository {

    private static final Logger log = LoggerFactory.getLogger(PostgresRiskIncidentRepository.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RiskIncidentJpaRepository jpa;

    public PostgresRiskIncidentRepository(RiskIncidentJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void record(RiskIncident incident) {
        jpa.save(toJpa(incident));
    }

    @Override
    public Optional<RiskIncident> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    // ──────────────────────── Mappers Domain ↔ JPA ────────────────────────

    private RiskIncidentJpa toJpa(RiskIncident d) {
        RiskIncidentJpa j = new RiskIncidentJpa();
        j.setId(d.id());
        j.setRule(d.rule());
        j.setUserId(d.userId() != null ? d.userId().uuid() : null);
        j.setWalletId(d.walletId());
        j.setSeverity(RiskIncidentJpa.RiskSeverity.valueOf(d.severity()));
        j.setStatus(RiskIncidentJpa.RiskStatus.valueOf(d.status()));
        j.setPayload(toJson(d.payload()));
        Instant created = d.createdAt() == null ? Instant.now() : d.createdAt();
        j.setCreatedByRuleAt(created);
        j.setCreatedAt(created);
        j.setUpdatedAt(created);
        return j;
    }

    private RiskIncident toDomain(RiskIncidentJpa j) {
        if (j == null) return null;
        return new RiskIncident(
                j.getId(),
                j.getRule(),
                j.getUserId() != null ? UserId.from(j.getUserId()) : null,
                j.getWalletId(),
                j.getSeverity() != null ? j.getSeverity().name() : "MEDIUM",
                j.getStatus() != null ? j.getStatus().name() : "OPEN",
                fromJson(j.getPayload()),
                j.getCreatedAt());
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (Exception e) {
            log.warn("No se pudo serializar payload de incidente de riesgo: {}", e.getMessage());
            return "{}";
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("No se pudo deserializar payload de incidente de riesgo: {}", e.getMessage());
            return Map.of();
        }
    }
}
