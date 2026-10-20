package com.micropay.backend.domain.entities;

import java.time.Instant;
import java.util.UUID;

/**
 * ReconciliationRun — ejecución del job de reconciliación interno vs proveedor
 * (T25 / Requisito 28.3). Java puro (sin Spring/JPA).
 */
public record ReconciliationRun(
        UUID id,
        UUID triggeredBy,
        String provider,
        String status,
        Instant periodStart,
        Instant periodEnd,
        int internalCount,
        int providerCount,
        int mismatchCount,
        String notes,
        Instant createdAt,
        Instant completedAt
) {

    public static final String RUNNING = "RUNNING";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";

    /** Crea un run en estado RUNNING. */
    public static ReconciliationRun running(UUID triggeredBy, String provider,
                                            Instant periodStart, Instant periodEnd) {
        return new ReconciliationRun(
                UUID.randomUUID(), triggeredBy, provider, RUNNING,
                periodStart, periodEnd, 0, 0, 0, null, Instant.now(), null);
    }

    public ReconciliationRun completed(int internalCount, int providerCount, int mismatchCount) {
        return new ReconciliationRun(id, triggeredBy, provider, COMPLETED,
                periodStart, periodEnd, internalCount, providerCount, mismatchCount,
                notes, createdAt, Instant.now());
    }

    public ReconciliationRun failed(String notes) {
        return new ReconciliationRun(id, triggeredBy, provider, FAILED,
                periodStart, periodEnd, internalCount, providerCount, mismatchCount,
                notes, createdAt, Instant.now());
    }
}
