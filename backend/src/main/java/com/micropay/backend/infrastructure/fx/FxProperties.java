package com.micropay.backend.infrastructure.fx;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Propiedades del adaptador FX (namespace {@code micropay.fx.*}).
 * <p>
 * Las {@code rates} son un mapa {@code <BASE><QUOTE> -> rate} donde rate == unidades de QUOTE
 * por 1 unidad de BASE (p. ej. {@code USDCOP: 4100.00} == 1 USD = 4100 COP).
 * Valores por defecto = set sandbox determinista; en producción se sustituyen por un
 * proveedor de tasas real (el adaptador es un stub configurable).
 */
@ConfigurationProperties(prefix = "micropay.fx")
public class FxProperties {

    /** Habilita/deshabilita el adaptador FX (independiente del flag de negocio multi-moneda). */
    private boolean enabled = true;

    /** Moneda pivote por defecto para derivar tasas ausentes. */
    private String defaultCurrency = "COP";

    /** Mapa de tasas BASE+QUOTE -> rate. */
    private Map<String, BigDecimal> rates = new LinkedHashMap<>();

    /** TTL (segundos) de la cache Redis de cotizaciones. */
    private long cacheTtlSeconds = 120;

    /** Tope sandbox (máx USD por operación) — informativo. */
    private BigDecimal sandboxMaxUsd = BigDecimal.ONE;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getDefaultCurrency() { return defaultCurrency; }
    public void setDefaultCurrency(String defaultCurrency) { this.defaultCurrency = defaultCurrency; }

    public Map<String, BigDecimal> getRates() { return rates; }
    public void setRates(Map<String, BigDecimal> rates) { this.rates = rates; }

    public long getCacheTtlSeconds() { return cacheTtlSeconds; }
    public void setCacheTtlSeconds(long cacheTtlSeconds) { this.cacheTtlSeconds = cacheTtlSeconds; }

    public BigDecimal getSandboxMaxUsd() { return sandboxMaxUsd; }
    public void setSandboxMaxUsd(BigDecimal sandboxMaxUsd) { this.sandboxMaxUsd = sandboxMaxUsd; }
}
