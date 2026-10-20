package com.micropay.backend.domain.valueobjects;

import java.util.Objects;
import java.util.UUID;

public record RefundId(UUID uuid) {

    public RefundId {
        Objects.requireNonNull(uuid, "refundId es requerido");
    }

    public static RefundId generate() {
        return new RefundId(UUID.randomUUID());
    }

    public static RefundId from(UUID uuid) {
        return new RefundId(uuid);
    }

    public static RefundId fromString(String uuid) {
        return new RefundId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return uuid.toString();
    }
}
