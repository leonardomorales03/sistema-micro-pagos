package com.micropay.backend.domain.valueobjects;

import com.micropay.backend.domain.exceptions.InvalidValueObjectException;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.regex.Pattern;

public record ShortCode(String value) {

    public static final int LENGTH = 6;
    private static final Pattern ALPHANUM = Pattern.compile("^[A-Za-z0-9]{" + LENGTH + "}$");
    private static final String CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RND = new SecureRandom();

    public ShortCode {
        Objects.requireNonNull(value, "shortCode es requerido");
        value = value.strip();
        if (!ALPHANUM.matcher(value).matches()) {
            throw new InvalidValueObjectException("ShortCode",
                    "debe tener exactamente %d caracteres alfanuméricos, recibió '%s'"
                            .formatted(LENGTH, value));
        }
    }

    /** Genera aleatorio (evita 0,O,1,I para evitar ambigüedad en QR) */
    public static ShortCode random() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(CHARSET.charAt(RND.nextInt(CHARSET.length())));
        }
        return new ShortCode(sb.toString());
    }
}
