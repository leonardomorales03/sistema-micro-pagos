package com.micropay.backend.domain.entities;

import com.micropay.backend.domain.valueobjects.UserId;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * RiskIncident — incidente de riesgo detectado por el RiskEngine (tabla {@code risk_incidents}, V1).
 * <p>
 * Registro de auditoría: rule (regla disparada), severidad, estado (OPEN por defecto),
 * payload con el contexto de la operación y marca temporal de creación.
 * {@code userId}/{@code walletId} pueden ser nulos (FK ON DELETE SET NULL).
 *
 * @param id        identificador del incidente
 * @param rule      código de la regla disparada
 * @param userId    usuario implicado (nullable)
 * @param walletId  wallet implicada (nullable)
 * @param severity  LOW | MEDIUM | HIGH | CRITICAL
 * @param status    OPEN | INVESTIGATING | RESOLVED | DISMISSED
 * @param payload   contexto de la operación
 * @param createdAt instante de creación
 */
public record RiskIncident(UUID id,
                           String rule,
                           UserId userId,
                           UUID walletId,
                           String severity,
                           String status,
                           Map<String, Object> payload,
                           Instant createdAt) {

    public RiskIncident {
        Objects.requireNonNull(id, "id es requerido");
        if (rule == null || rule.isBlank()) throw new IllegalArgumentException("rule es requerido");
        severity = severity == null ? "MEDIUM" : severity;
        status = status == null ? "OPEN" : status;
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    /** Nuevo incidente abierto (status OPEN, id y createdAt generados). */
    public static RiskIncident open(String rule, UserId userId, UUID walletId,
                                    String severity, Map<String, Object> payload) {
        return new RiskIncident(UUID.randomUUID(), rule, userId, walletId,
                severity, "OPEN", payload, Instant.now());
    }
}
