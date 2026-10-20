package com.micropay.backend.domain.valueobjects;

/**
 * Tipo de comisión aplicable por la plataforma (Requisito 30.1).
 *  - FLAT:      comisión fija (ej. retiro).
 *  - PERCENTAGE: comisión porcentual sobre el monto (ej. MDR de Payment Request, top-up).
 *  - FX_SPREAD: margen de conversión de divisas (multi-moneda).
 */
public enum FeeType {
    FLAT("Comisión fija por operación"),
    PERCENTAGE("Comisión porcentual sobre el monto"),
    FX_SPREAD("Margen de conversión de divisas (FX)");

    private final String description;

    FeeType(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
