package com.micropay.backend.domain.valueobjects;

public enum Currency {
    COP("Peso Colombiano", "COP", "$", 0),
    USD("Dólar Estadounidense", "USD", "US$", 2),
    EUR("Euro", "EUR", "€", 2);

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
}
