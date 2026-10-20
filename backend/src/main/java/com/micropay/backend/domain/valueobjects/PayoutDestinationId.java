package com.micropay.backend.domain.valueobjects;

import java.util.Objects;
import java.util.UUID;

public record PayoutDestinationId(UUID uuid) {

    public PayoutDestinationId {
        Objects.requireNonNull(uuid, "payoutDestinationId es requerido");
    }

    public static PayoutDestinationId generate() {
        return new PayoutDestinationId(UUID.randomUUID());
    }

    public static PayoutDestinationId from(UUID uuid) {
        return new PayoutDestinationId(uuid);
    }

    public static PayoutDestinationId fromString(String uuid) {
        return new PayoutDestinationId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return uuid.toString();
    }
}
