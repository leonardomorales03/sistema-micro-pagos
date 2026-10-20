package com.micropay.backend.domain.exceptions;

import java.util.UUID;

public final class ReconciliationMismatchNotFoundException extends DomainException {

    public ReconciliationMismatchNotFoundException(UUID id) {
        super("RECONCILIATION_MISMATCH_NOT_FOUND",
                "Discrepancia de reconciliación con id '%s' no existe".formatted(id));
    }
}
