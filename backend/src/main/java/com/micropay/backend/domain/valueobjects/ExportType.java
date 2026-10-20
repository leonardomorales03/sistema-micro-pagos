package com.micropay.backend.domain.valueobjects;

/**
 * Tipos de exportación CSV soportados (T25 / Requisito 28.1).
 */
public enum ExportType {
    TX_HISTORY,
    TOPUPS,
    WITHDRAWALS,
    ANNUAL_TAX_SUMMARY;

    public static ExportType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("type de exportación es requerido");
        }
        return ExportType.valueOf(raw.trim().toUpperCase());
    }

    public static boolean isValid(String raw) {
        if (raw == null || raw.isBlank()) return false;
        try {
            ExportType.valueOf(raw.trim().toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
