package com.micropay.backend.domain.exceptions;

public final class BusinessRuleViolationException extends DomainException {

    public BusinessRuleViolationException(String rule, String description) {
        super("BUSINESS_RULE_VIOLATION_" + rule,
                "Regla de negocio violada [%s]: %s".formatted(rule, description));
    }
}
