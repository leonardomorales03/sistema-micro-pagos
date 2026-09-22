package com.micropay.backend.domain.exceptions;

/**
 * Excepción lanzada cuando una operación concurrente supera el número máximo de reintentos.
 * Normalmente originada por:
 *   - OptimisticLockingFailureException (@Version)
 *   - Deadlock PSQL 40P01 (deadlock detected)
 *   - CannotAcquireLockException (select for update nowait / lock timeout)
 *   - WALLET_LOCK_CONFLICT (Redis SETNX distribuido)
 * Cliente debe reintentar con backoff. HTTP status=409 + Retry-After.
 */
public final class ConcurrencyConflictException extends DomainException {

    private final int attempts;
    private final String operation;

    public ConcurrencyConflictException(String errorCode, String detail, int attempts, String operation, Throwable cause) {
        super(errorCode, detail, cause);
        this.attempts = attempts;
        this.operation = operation;
    }

    public ConcurrencyConflictException(String errorCode, String detail, int attempts, String operation) {
        this(errorCode, detail, attempts, operation, null);
    }

    public int attempts() {
        return attempts;
    }

    public String operation() {
        return operation;
    }

    public String errorCode() {
        return getCode();
    }
}
