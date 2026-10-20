package com.micropay.backend.domain.valueobjects;

/**
 * Contexto de una operación a evaluar por el RiskEngine (T23 / Requisito 27).
 * <p>
 * Tolerante por diseño: {@code kycLevel} y {@code countryISO2} pueden venir nulos
 * cuando el perfil no está disponible; el motor aplica defaults permisivos.
 *
 * @param userId            usuario que inicia la operación
 * @param kycLevel          nivel KYC del usuario (nullable)
 * @param countryISO2       país ISO2 del usuario en mayúsculas (nullable)
 * @param operation         operación evaluada
 * @param amount            monto de la operación
 * @param ipAddress         IP de origen (nullable)
 * @param deviceFingerprint huella del dispositivo (nullable)
 */
public record RiskContext(UserId userId,
                          KycLevel kycLevel,
                          String countryISO2,
                          RiskOperation operation,
                          Money amount,
                          String ipAddress,
                          String deviceFingerprint) {
}
