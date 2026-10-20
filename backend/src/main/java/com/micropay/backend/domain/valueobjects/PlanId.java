package com.micropay.backend.domain.valueobjects;

import java.util.Objects;
import java.util.UUID;

public record PlanId(UUID uuid) {

    public PlanId {
        Objects.requireNonNull(uuid, "planId es requerido");
    }

    public static PlanId generate() {
        return new PlanId(UUID.randomUUID());
    }

    public static PlanId from(UUID uuid) {
        return new PlanId(uuid);
    }

    public static PlanId fromString(String uuid) {
        return new PlanId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return uuid.toString();
    }
}
