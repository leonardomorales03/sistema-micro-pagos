package com.micropay.backend.infrastructure.fx;

import com.micropay.backend.domain.ports.out.FxRatePort;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.FxQuote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador FX — stub CONFIGURABLE con cache Redis (T26 / Requisitos 29.2, 29.3).
 * <p>
 * NO realiza llamadas HTTP a un proveedor real: lee las tasas de {@code micropay.fx.rates}
 * (set sandbox determinista por defecto). En producción este adaptador se sustituye por uno
 * que consulte un proveedor externo de tipos de cambio; el contrato ({@link FxRatePort}) no cambia.
 * <p>
 * Estrategia de resolución para {@code base -> quote}:
 * <ol>
 *   <li>identidad si {@code base == quote};</li>
 *   <li>tasa directa configurada ({@code BASEQUOTE}) o su inversa ({@code QUOTEBASE});</li>
 *   <li>derivación vía pivote (USD, moneda por defecto) multiplicando dos tasas conocidas;</li>
 *   <li>fallback final: rate = 1 con warning (no rompe el flujo, queda trazado).</li>
 * </ol>
 * Las tasas resueltas se cachean en Redis bajo {@code fxrate:{base}:{quote}} con TTL corto
 * ({@code micropay.fx.cache-ttl-seconds}, por defecto 120s). Si Redis no está disponible,
 * se resuelve en cada llamada sin cache (modo permisivo).
 */
@Component
public class ConfigurableFxRateAdapter implements FxRatePort {

    private static final Logger log = LoggerFactory.getLogger(ConfigurableFxRateAdapter.class);
    private static final String KEY_PREFIX = "fxrate:";
    private static final String SOURCE = "CONFIG_SANDBOX";

    private final FxProperties props;
    private final StringRedisTemplate redis;

    public ConfigurableFxRateAdapter(FxProperties props, StringRedisTemplate redis) {
        this.props = props;
        this.redis = redis;
    }

    @Override
    public FxQuote quote(Currency base, Currency quote) {
        if (base == null || quote == null) {
            throw new IllegalArgumentException("base y quote son requeridos para cotizar FX");
        }
        String cacheKey = KEY_PREFIX + base.isoCode() + ":" + quote.isoCode();
        Optional<BigDecimal> cached = readCache(cacheKey);
        if (cached.isPresent()) {
            return new FxQuote(base, quote, cached.get(), SOURCE + ":CACHE", Instant.now());
        }
        Resolved resolved = resolve(base, quote);
        writeCache(cacheKey, resolved.rate());
        return new FxQuote(base, quote, resolved.rate(), resolved.source(), Instant.now());
    }

    // ──────────────────────── resolución ────────────────────────

    private Resolved resolve(Currency base, Currency quote) {
        if (base == quote) {
            return new Resolved(BigDecimal.ONE, SOURCE + ":IDENTITY");
        }
        Optional<BigDecimal> direct = tryRate(base, quote);
        if (direct.isPresent()) {
            return new Resolved(direct.get(), SOURCE + ":DIRECT");
        }
        for (Currency pivot : pivots()) {
            if (pivot == base || pivot == quote) continue;
            Optional<BigDecimal> b = tryRate(base, pivot);
            Optional<BigDecimal> c = tryRate(pivot, quote);
            if (b.isPresent() && c.isPresent()) {
                return new Resolved(b.get().multiply(c.get()), SOURCE + ":FALLBACK_VIA_" + pivot.isoCode());
            }
        }
        log.warn("FX rate no configurada para {}->{}; se aplica rate=1 (revisar micropay.fx.rates)",
                base.isoCode(), quote.isoCode());
        return new Resolved(BigDecimal.ONE, SOURCE + ":FALLBACK_IDENTITY");
    }

    /** Tasa directa o inversa desde la config; vacío si no está definida. */
    private Optional<BigDecimal> tryRate(Currency from, Currency to) {
        if (from == to) return Optional.of(BigDecimal.ONE);
        BigDecimal direct = props.getRates().get(from.isoCode() + to.isoCode());
        if (direct != null && direct.signum() > 0) {
            return Optional.of(direct);
        }
        BigDecimal inverse = props.getRates().get(to.isoCode() + from.isoCode());
        if (inverse != null && inverse.signum() > 0) {
            return Optional.of(BigDecimal.ONE.divide(inverse, MathContext.DECIMAL64));
        }
        return Optional.empty();
    }

    private List<Currency> pivots() {
        Currency def = Currency.COP;
        try {
            def = Currency.fromIso(props.getDefaultCurrency());
        } catch (Exception ignored) {
            // default-currency inválida: nos quedamos con COP como pivote
        }
        return def == Currency.USD ? List.of(Currency.USD) : List.of(Currency.USD, def);
    }

    // ──────────────────────── cache Redis ────────────────────────

    private Optional<BigDecimal> readCache(String key) {
        try {
            String raw = redis.opsForValue().get(key);
            if (raw == null || raw.isBlank()) return Optional.empty();
            return Optional.of(new BigDecimal(raw));
        } catch (Exception e) {
            log.debug("Cache FX no disponible para key={}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    private void writeCache(String key, BigDecimal rate) {
        try {
            long ttl = props.getCacheTtlSeconds() > 0 ? props.getCacheTtlSeconds() : 120L;
            redis.opsForValue().set(key, rate.toPlainString(), Duration.ofSeconds(ttl));
        } catch (Exception e) {
            log.debug("No se pudo cachear FX key={}: {}", key, e.getMessage());
        }
    }

    private record Resolved(BigDecimal rate, String source) {}
}
