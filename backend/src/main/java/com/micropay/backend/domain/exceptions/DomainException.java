package com.micropay.backend.domain.exceptions;

/**
 * Excepción base de dominio. Todas las reglas de negocio lanzan
 * subtipos de esta excepción; Infrastructure / API la traducen a
 * HTTP status code apropiado.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    protected DomainException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
