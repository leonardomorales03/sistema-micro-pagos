package com.micropay.backend.domain.valueobjects;

import com.micropay.backend.domain.exceptions.InvalidValueObjectException;

import java.util.Objects;
import java.util.regex.Pattern;

public record Email(String value) {

    private static final Pattern RFC_5322 = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    private static final int MAX_LEN = 254;

    public Email {
        Objects.requireNonNull(value, "email es requerido");
        value = value.strip().toLowerCase();
        if (value.isEmpty()) {
            throw new InvalidValueObjectException("Email", "no puede estar vacío");
        }
        if (value.length() > MAX_LEN) {
            throw new InvalidValueObjectException("Email",
                    "longitud excede %d caracteres".formatted(MAX_LEN));
        }
        if (!RFC_5322.matcher(value).matches()) {
            throw new InvalidValueObjectException("Email",
                    "formato inválido (%s)".formatted(value));
        }
    }
}
