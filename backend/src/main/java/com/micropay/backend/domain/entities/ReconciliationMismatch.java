package com.micropay.backend.domain.entities;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * ReconciliationMismatch — discrepancia detectada por un {@link ReconciliationRun}
 * (T25 / Requisito 28.3). Java puro (sin Spring/JPA).
 * <p>
 * {@code kind}: MISSING_AT_PROVIDER | AMOUNT_MISMATCH | UNEXPECTED_PROVIDER_ENTRY.
 * {@code status}: NEW → INVESTIGATING → RESOLVED.
 */
public record ReconciliationMismatch(
        UUID id,
        UUID runId,
        String kind,
        UUID internalTxId,
        String providerRef,
        BigDecimal internalAmount,
        BigDecimal providerAmount,
        String currency,
        String status,
        String resolutionNote,
        UUID resolvedBy,
        Instant createdAt,
        Instant updatedAt
) {

    public static final String NEW = "NEW";
    public static final String INVESTIGATING = "INVESTIGATING";
    public static final String RESOLVED = "RESOLVED";

    public static final String KIND_MISSING_AT_PROVIDER = "MISSING_AT_PROVIDER";
    public static final String KIND_AMOUNT_MISMATCH = "AMOUNT_MISMATCH";
    public static final String KIND_UNEXPECTED_PROVIDER_ENTRY = "UNEXPECTED_PROVIDER_ENTRY";

    /** Crea una nueva discrepancia en estado NEW. */
    public static ReconciliationMismatch create(UUID runId, String kind, UUID internalTxId,
                                                String providerRef, BigDecimal internalAmount,
                                                BigDecimal providerAmount, String currency) {
        Instant now = Instant.now();
        return new ReconciliationMismatch(
                UUID.randomUUID(), runId, kind, internalTxId, providerRef,
                internalAmount, providerAmount, currency, NEW, null, null, now, now);
    }
}
