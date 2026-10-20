package com.micropay.backend.domain.valueobjects;

import java.time.Instant;

/**
 * ProviderEntry — entrada del proveedor externo usada en reconciliación
 * (T25 / Requisito 28.3). Java puro (sin Spring).
 *
 * @param providerRef referencia única de la operación en el proveedor
 * @param amount      monto reportado por el proveedor
 * @param currency    moneda ISO-4217 (3 chars)
 * @param type        tipo de operación reportada por el proveedor
 * @param occurredAt  instante en que el proveedor registró la operación
 */
public record ProviderEntry(
        String providerRef,
        java.math.BigDecimal amount,
        String currency,
        String type,
        Instant occurredAt
) {}
