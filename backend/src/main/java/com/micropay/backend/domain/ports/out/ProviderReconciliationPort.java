package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.valueobjects.ProviderEntry;

import java.time.Instant;
import java.util.List;

/**
 * Port OUT — Fuente de entradas del proveedor para reconciliación (T25 / Requisito 28.3).
 * Implementado en infraestructura por un adaptador sandbox que deriva las entradas de las
 * transacciones internas; en producción se sustituye por el CSV/API real del proveedor.
 */
public interface ProviderReconciliationPort {

    /**
     * Obtiene las entradas reportadas por {@code provider} dentro de {@code [periodStart, periodEnd]}.
     */
    List<ProviderEntry> fetchEntries(String provider, Instant periodStart, Instant periodEnd);
}
