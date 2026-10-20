package com.micropay.backend.domain.valueobjects;

import java.util.Objects;
import java.util.UUID;

public record SubscriptionId(UUID uuid) {

    public SubscriptionId {
        Objects.requireNonNull(uuid, "subscriptionId es requerido");
    }

    public static SubscriptionId generate() {
        return new SubscriptionId(UUID.randomUUID());
    }

    public static SubscriptionId from(UUID uuid) {
        return new SubscriptionId(uuid);
    }

    public static SubscriptionId fromString(String uuid) {
        return new SubscriptionId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return uuid.toString();
    }
}
