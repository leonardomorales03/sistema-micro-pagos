package com.micropay.backend.domain.valueobjects;

import java.util.List;
import java.util.Objects;

/**
 * Decisión del RiskEngine (T23 / Requisito 27).
 *
 * @param type            ALLOW | CHALLENGE | BLOCK
 * @param reasons         motivos asociados (puede ser vacío)
 * @param challengeToken  token del challenge (sólo CHALLENGE generado por el servicio)
 * @param challengeMethod método del challenge (p. ej. EMAIL_CODE)
 */
public record RiskDecision(RiskDecisionType type,
                           List<RiskReason> reasons,
                           String challengeToken,
                           String challengeMethod) {

    public RiskDecision {
        Objects.requireNonNull(type, "type es requerido");
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    /** Decisión permisiva sin motivos. */
    public static RiskDecision allow() {
        return new RiskDecision(RiskDecisionType.ALLOW, List.of(), null, null);
    }

    /** Decisión de bloqueo con uno o más motivos. */
    public static RiskDecision block(RiskReason... reasons) {
        return new RiskDecision(RiskDecisionType.BLOCK, List.of(reasons), null, null);
    }

    /** Decisión de step-up (challenge). El token lo genera el servicio de aplicación. */
    public static RiskDecision challenge(String method, String token, RiskReason... reasons) {
        return new RiskDecision(RiskDecisionType.CHALLENGE, List.of(reasons), token, method);
    }

    public boolean isAllowed() {
        return type == RiskDecisionType.ALLOW;
    }

    public boolean requiresChallenge() {
        return type == RiskDecisionType.CHALLENGE;
    }

    public boolean isBlocked() {
        return type == RiskDecisionType.BLOCK;
    }
}
