package com.micropay.backend.domain.valueobjects;

/**
 * Distingue un reembolso total del original (FULL) de uno parcial (PARTIAL)
 * — Requisito 23.5.
 */
public enum RefundType {
    FULL("Reembolso total del monto original"),
    PARTIAL("Reembolso parcial del monto original");

    private final String description;

    RefundType(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
