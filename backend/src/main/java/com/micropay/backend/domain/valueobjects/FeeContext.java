package com.micropay.backend.domain.valueobjects;

import java.util.Objects;

/**
 * Contexto de cálculo de comisiones (Requisito 30.1).
 * Describe la operación, el monto base, el tier KYC del pagador y el país,
 * de forma que el FeeEngine pueda aplicar reglas diferenciadas.
 */
public record FeeContext(FeeOperation operation, Money amount, KycLevel tier, String country) {

    public FeeContext {
        Objects.requireNonNull(operation, "operation requerido");
        Objects.requireNonNull(amount, "amount requerido");
    }

    public static FeeContext of(FeeOperation operation, Money amount) {
        return new FeeContext(operation, amount, null, null);
    }

    public Currency currency() {
        return amount.currency();
    }
}
