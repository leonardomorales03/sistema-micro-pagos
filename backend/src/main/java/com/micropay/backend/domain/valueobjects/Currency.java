package com.micropay.backend.domain.valueobjects;

import com.micropay.backend.domain.exceptions.InvalidValueObjectException;

import java.util.List;
import java.util.Locale;

public enum Currency {
    COP("Peso Colombiano", "COP", "$", 0),
    USD("Dólar Estadounidense", "USD", "US$", 2),
    EUR("Euro", "EUR", "€", 2),
    ARS("Peso Argentino", "ARS", "$", 2),
    BRL("Real Brasileño", "BRL", "R$", 2),
    JPY("Yen Japonés", "JPY", "¥", 0);

    private final String displayName;
    private final String isoCode;
    private final String symbol;
    private final int defaultFractionDigits;

    Currency(String displayName, String isoCode, String symbol, int defaultFractionDigits) {
        this.displayName = displayName;
        this.isoCode = isoCode;
        this.symbol = symbol;
        this.defaultFractionDigits = defaultFractionDigits;
    }

    public String displayName() {
        return displayName;
    }

    public String isoCode() {
        return isoCode;
    }

    public String symbol() {
        return symbol;
    }

    public int defaultFractionDigits() {
        return defaultFractionDigits;
    }

    /**
     * Resuelve una moneda a partir de su código ISO (case-insensitive).
     *
     * @throws InvalidValueObjectException si el ISO no está soportado.
     */
    public static Currency fromIso(String iso) {
        if (iso == null || iso.isBlank()) {
            throw new InvalidValueObjectException("Currency.iso", "el código ISO es requerido");
        }
        String normalized = iso.trim().toUpperCase(Locale.ROOT);
        for (Currency c : values()) {
            if (c.isoCode.equals(normalized)) {
                return c;
            }
        }
        throw new InvalidValueObjectException("Currency.iso", "moneda no soportada: " + iso);
    }

    /** Monedas soportadas por el sistema (orden de declaración). */
    public static List<Currency> supported() {
        return List.of(values());
    }
}
