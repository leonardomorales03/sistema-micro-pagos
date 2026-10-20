package com.micropay.backend.domain.valueobjects;

import java.util.Objects;

/**
 * FeeLine — Valor de una comisión calculada por el FeeEngine (Requisito 30.1).
 * <p>
 * Inmutable. {@code ruleVersion} referencia la versión de la regla de pricing aplicada
 * en el momento de la ejecución (Requisito 30.4), garantizando trazabilidad histórica.
 */
public record FeeLine(Money amount, FeeType type, String ruleVersion, String description) {

    public FeeLine {
        Objects.requireNonNull(amount, "amount requerido");
        Objects.requireNonNull(type, "type requerido");
        Objects.requireNonNull(ruleVersion, "ruleVersion requerido");
    }

    /** ¿La comisión es cero? (no genera asiento de ledger). */
    public boolean isZero() {
        return amount.isZero();
    }

    public Currency currency() {
        return amount.currency();
    }
}
