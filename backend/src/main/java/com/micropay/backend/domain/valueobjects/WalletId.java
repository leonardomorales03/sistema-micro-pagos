package com.micropay.backend.domain.valueobjects;

import java.util.Objects;
import java.util.UUID;

public record WalletId(UUID uuid) {

    public WalletId {
        Objects.requireNonNull(uuid, "walletId es requerido");
    }

    /** UUID v4 estándar (consistencia con UserId/TransactionId) */
    public static WalletId generate() {
        return new WalletId(UUID.randomUUID());
    }

    public static WalletId from(UUID uuid) {
        return new WalletId(uuid);
    }

    public static WalletId fromString(String uuid) {
        return new WalletId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return uuid.toString();
    }
}
