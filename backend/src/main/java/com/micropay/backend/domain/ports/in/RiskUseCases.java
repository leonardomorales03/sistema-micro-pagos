package com.micropay.backend.domain.ports.in;

import com.micropay.backend.domain.valueobjects.RiskContext;
import com.micropay.backend.domain.valueobjects.RiskDecision;

/**
 * Casos de uso IN del RiskEngine (T23 / Requisito 27).
 * Implementado en la capa de aplicación ({@code RiskService}).
 */
public interface RiskUseCases {

    /** Datos de emisión de un challenge de step-up. */
    record ChallengeIssue(String token, String method, int expiresInSeconds) {}

    /** Evalúa el contexto y devuelve la decisión del motor. No lanza. */
    RiskDecision evaluate(RiskContext ctx);

    /**
     * Aplica la política de riesgo sobre una operación sensible:
     *   - ALLOW → retorna.
     *   - BLOCK → registra incidente y lanza {@code BusinessRuleViolationException("RISK_BLOCKED")}.
     *   - CHALLENGE → si {@code challengeApprovalToken} ya está aprobado retorna; si no,
     *     emite challenge (código por EMAIL) y lanza {@code RiskChallengeRequiredException}.
     */
    void enforce(RiskContext ctx, String challengeApprovalToken);

    /** Valida el código de un challenge y marca el token como aprobado. Devuelve el token aprobado. */
    String verifyChallenge(String token, String code);
}
