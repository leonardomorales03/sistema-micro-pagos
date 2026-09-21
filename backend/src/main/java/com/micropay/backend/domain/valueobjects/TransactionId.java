package com.micropay.backend.domain.valueobjects;

import java.util.Objects;
import java.util.UUID;

public record TransactionId(UUID uuid) {

    public TransactionId {
        Objects.requireNonNull(uuid, "transactionId es requerido");
    }

    /** UUID v4 estándar. */
    public static TransactionId generate() {
        return new TransactionId(UUID.randomUUID());
    }

    public static TransactionId from(UUID uuid) {
        return new TransactionId(uuid);
    }

    public static TransactionId fromString(String uuid) {
        return new TransactionId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return uuid.toString();
    }
}
