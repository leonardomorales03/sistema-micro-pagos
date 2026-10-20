package com.micropay.backend.domain.entities;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * ReportExport — job de exportación CSV asíncrona (T25 / Requisito 28.1).
 * <p>
 * Java puro (sin Spring/JPA). El adaptador de infraestructura lo persiste en
 * la tabla {@code report_exports} (V11). Ciclo de vida:
 * {@code QUEUED → RUNNING → COMPLETED | FAILED}.
 */
public record ReportExport(
        UUID id,
        UUID requestedBy,
        String type,
        Map<String, Object> filters,
        String status,
        String filePath,
        String downloadToken,
        Long rowCount,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant expiresAt
) {

    public static final String QUEUED = "QUEUED";
    public static final String RUNNING = "RUNNING";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";

    /** Encola un nuevo job en estado QUEUED. */
    public static ReportExport queued(UUID requestedBy, String type, Map<String, Object> filters) {
        return new ReportExport(
                UUID.randomUUID(),
                requestedBy,
                type,
                filters != null ? filters : Map.of(),
                QUEUED,
                null, null, null, null,
                Instant.now(),
                null, null, null);
    }

    public ReportExport running(Instant startedAt) {
        return new ReportExport(id, requestedBy, type, filters, RUNNING,
                null, null, null, null, createdAt, startedAt, null, null);
    }

    public ReportExport completed(String filePath, String downloadToken, long rowCount, Instant expiresAt) {
        return new ReportExport(id, requestedBy, type, filters, COMPLETED,
                filePath, downloadToken, rowCount, null, createdAt,
                startedAt != null ? startedAt : Instant.now(), Instant.now(), expiresAt);
    }

    public ReportExport failed(String errorMessage) {
        return new ReportExport(id, requestedBy, type, filters, FAILED,
                null, null, null, errorMessage, createdAt,
                startedAt, Instant.now(), null);
    }
}
