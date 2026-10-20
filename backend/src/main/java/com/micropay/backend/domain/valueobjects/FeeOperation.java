package com.micropay.backend.domain.valueobjects;

/**
 * Operación de negocio sobre la que el FeeEngine calcula comisiones.
 * Cada operación tiene su propia regla de pricing configurable (Requisito 30.3).
 */
public enum FeeOperation {
    TRANSFER_P2P("Transferencia entre usuarios"),
    TOPUP("Recarga / depósito"),
    WITHDRAWAL("Retiro a cuenta externa"),
    PAYMENT_REQUEST("Pago de Payment Request (MDR)"),
    SUBSCRIPTION("Cobro recurrente de suscripción"),
    REFUND("Reembolso");

    private final String description;

    FeeOperation(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
