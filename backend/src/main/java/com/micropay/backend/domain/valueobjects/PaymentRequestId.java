package com.micropay.backend.domain.valueobjects;

import java.util.Objects;
import java.util.UUID;

public record PaymentRequestId(UUID uuid) {

    public PaymentRequestId {
        Objects.requireNonNull(uuid, "paymentRequestId es requerido");
    }

    public static PaymentRequestId generate() {
        return new PaymentRequestId(UUID.randomUUID());
    }

    public static PaymentRequestId from(UUID uuid) {
        return new PaymentRequestId(uuid);
    }

    public static PaymentRequestId fromString(String uuid) {
        return new PaymentRequestId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return uuid.toString();
    }
}
