package com.micropay.backend.domain.valueobjects;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * AnnualTaxLine — línea del resumen fiscal anual por usuario/categoría
 * (T25 / Requisito 28.1, export ANNUAL_TAX_SUMMARY). Java puro (sin Spring).
 */
public record AnnualTaxLine(
        UUID userId,
        String type,
        String currency,
        BigDecimal totalGross,
        BigDecimal totalFee,
        BigDecimal totalNet,
        long count
) {}
