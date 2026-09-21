package com.micropay.backend.domain.valueobjects;

public enum TransactionStatus {
    PENDING("Pendiente de procesamiento"),
    PROCESSING("En curso (no final)"),
    COMPLETED("Exitosa y final"),
    FAILED("Fallida, no afectó wallets"),
    CANCELLED("Cancelada por usuario/sistema"),
    REFUNDED("Completada y posteriormente reembolsada"),
    EXPIRED("PaymentRequest venció sin pagarse"),
    ON_HOLD("Con retención preventiva (KYC pendiente / Risk)");

    private final String description;

    TransactionStatus(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    /** ¿Estado terminal? = no habrá más transiciones */
    public boolean isTerminal() {
        return this == COMPLETED
                || this == FAILED
                || this == CANCELLED
                || this == REFUNDED
                || this == EXPIRED;
    }
}
