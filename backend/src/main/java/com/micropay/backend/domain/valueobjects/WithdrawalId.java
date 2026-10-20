package com.micropay.backend.domain.valueobjects;

import java.util.Objects;
import java.util.UUID;

public record WithdrawalId(UUID uuid) {

    public WithdrawalId {
        Objects.requireNonNull(uuid, "withdrawalId es requerido");
    }

    public static WithdrawalId generate() {
        return new WithdrawalId(UUID.randomUUID());
    }

    public static WithdrawalId from(UUID uuid) {
        return new WithdrawalId(uuid);
    }

    public static WithdrawalId fromString(String uuid) {
        return new WithdrawalId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return uuid.toString();
    }
}
