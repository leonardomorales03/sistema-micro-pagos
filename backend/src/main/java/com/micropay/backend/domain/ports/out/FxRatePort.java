package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.FxQuote;

/**
 * Port OUT — Proveedor de tipos de cambio (FX).
 * Implementado por Infrastructure Adapter → ConfigurableFxRateAdapter
 * (stub configurable con cache Redis; en producción se sustituye por un proveedor externo real).
 */
public interface FxRatePort {

    /**
     * Obtiene la cotización {@code base → quote}.
     * Se permite {@code base == quote} (rate = 1).
     */
    FxQuote quote(Currency base, Currency quote);
}
