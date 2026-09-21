package com.micropay.backend.domain.valueobjects;

public enum TransactionType {
    TRANSFER_P2P("Transferencia entre usuarios"),
    DEPOSIT_TOPUP("Depósito (TopUp vía tarjeta/transferencia bancaria)"),
    WITHDRAW("Retiro a cuenta bancaria"),
    PAYMENT_REQUEST("Pago por QR PaymentRequest"),
    SUBSCRIPTION_CHARGE("Cobro recurrente suscripción"),
    REFUND("Reembolso"),
    REFERRAL_REWARD("Recompensa por referido"),
    FEE_ADJUSTMENT("Ajuste manual de comisiones (Admin)"),
    FX_CONVERSION("Conversión FX (multi-moneda)");

    private final String description;

    TransactionType(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
