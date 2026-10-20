package com.micropay.backend.domain.valueobjects;

/**
 * Operación sujeta a evaluación de riesgo (T23 / Requisito 27).
 */
public enum RiskOperation {
    P2P_TRANSFER,
    TOPUP,
    WITHDRAWAL,
    REFUND,
    PAYMENT_REQUEST
}
