package com.micropay.backend.domain.exceptions;

public final class InvalidValueObjectException extends DomainException {

    public InvalidValueObjectException(String field, String message) {
        super("INVALID_VALUE_OBJECT", "Valor inválido para '%s': %s".formatted(field, message));
    }
}
