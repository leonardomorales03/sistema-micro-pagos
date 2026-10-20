package com.micropay.backend.domain.valueobjects;

/**
 * Tipo de decisión emitida por el RiskEngine (T23 / Requisito 27).
 * <p>
 *   - ALLOW: la operación puede continuar sin pasos adicionales.
 *   - CHALLENGE: requiere step-up (código enviado por email) antes de continuar.
 *   - BLOCK: la operación se rechaza y se registra un incidente de auditoría.
 */
public enum RiskDecisionType {
    ALLOW,
    CHALLENGE,
    BLOCK
}
