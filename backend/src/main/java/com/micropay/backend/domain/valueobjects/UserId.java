package com.micropay.backend.domain.valueobjects;

import java.util.Objects;
import java.util.UUID;

/**
 * Typed UUID para User — evita errores "swap id" entre entidades
 * al pasar UUIDs por métodos. Se comporta como record sobre UUID.
 * <p>
 * Usa UUID v4 estándar java.util.UUID (sin dependencias externas).
 * En producción se puede reemplazar con UUID v7 time-ordered para
 * mejor rendimiento en índices Postgres.
 */
public record UserId(UUID uuid) {

    public UserId {
        Objects.requireNonNull(uuid, "userId es requerido");
    }

    /** Genera UUID v4 criptográficamente seguro. */
    public static UserId generate() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId from(UUID uuid) {
        return new UserId(uuid);
    }

    public static UserId fromString(String uuid) {
        return new UserId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return uuid.toString();
    }
}
