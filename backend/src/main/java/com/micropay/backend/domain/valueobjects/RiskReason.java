package com.micropay.backend.domain.valueobjects;

/**
 * Motivo asociado a una decisión de riesgo.
 *
 * @param code     código de la regla que disparó la decisión (p. ej. VELOCITY_DAY)
 * @param message  descripción legible del motivo
 * @param severity severidad: LOW | MEDIUM | HIGH | CRITICAL
 */
public record RiskReason(String code, String message, String severity) {

    public static final String LOW = "LOW";
    public static final String MEDIUM = "MEDIUM";
    public static final String HIGH = "HIGH";
    public static final String CRITICAL = "CRITICAL";

    public RiskReason {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("RiskReason.code es requerido");
        }
    }
}
