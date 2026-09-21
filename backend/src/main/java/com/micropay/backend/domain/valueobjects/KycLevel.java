package com.micropay.backend.domain.valueobjects;

public enum KycLevel {
    LEVEL_0("Nivel 0 - Básico",
            1_000_000L,   // COP / día
            5_000_000L),  // COP / mes
    LEVEL_1("Nivel 1 - Verificado",
            10_000_000L,
            50_000_000L),
    LEVEL_2("Nivel 2 - Validado completo",
            50_000_000L,
            300_000_000L);

    private final String label;
    private final long dailyLimitCop;
    private final long monthlyLimitCop;

    KycLevel(String label, long dailyLimitCop, long monthlyLimitCop) {
        this.label = label;
        this.dailyLimitCop = dailyLimitCop;
        this.monthlyLimitCop = monthlyLimitCop;
    }

    public String label() {
        return label;
    }

    public long dailyLimitCop() {
        return dailyLimitCop;
    }

    public long monthlyLimitCop() {
        return monthlyLimitCop;
    }

    public boolean atLeast(KycLevel other) {
        return this.ordinal() >= other.ordinal();
    }
}
