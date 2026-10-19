package com.micropay.backend.domain.valueobjects;

import java.util.Objects;
import java.util.UUID;

public record TopUpId(UUID uuid) {

    public TopUpId {
        Objects.requireNonNull(uuid, "topUpId es requerido");
    }

    public static TopUpId generate() {
        return new TopUpId(UUID.randomUUID());
    }

    public static TopUpId from(UUID uuid) {
        return new TopUpId(uuid);
    }

    public static TopUpId fromString(String uuid) {
        return new TopUpId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return uuid.toString();
    }
}
