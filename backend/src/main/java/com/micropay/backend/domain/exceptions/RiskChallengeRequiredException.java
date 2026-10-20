package com.micropay.backend.domain.exceptions;

import java.util.List;
import java.util.Optional;

/**
 * Se requiere un paso adicional (step-up / challenge) antes de continuar con la operación.
 * El API la traduce a HTTP 428 PRECONDITION_REQUIRED con el token y método en cabeceras.
 */
public final class RiskChallengeRequiredException extends RuntimeException {

    private final String challengeToken;
    private final String challengeMethod;
    private final List<String> reasons;

    public RiskChallengeRequiredException(String challengeToken, String challengeMethod, List<String> reasons) {
        super("Se requiere verificación adicional de riesgo (challenge %s)".formatted(challengeMethod));
        this.challengeToken = challengeToken;
        this.challengeMethod = challengeMethod;
        this.reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public String challengeToken() { return challengeToken; }

    public String challengeMethod() { return challengeMethod; }

    public List<String> reasons() { return reasons; }

    /** Códigos de motivo que dispararon el challenge (nunca null). */
    public List<String> reasonCodes() { return Optional.ofNullable(reasons).orElseGet(List::of); }
}
