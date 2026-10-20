package com.micropay.backend.domain.valueobjects;

import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 * FxQuote — cotización de tipo de cambio entre dos monedas (Value Object, Java puro).
 * <p>
 * {@code rate} significa: 1 unidad de {@code base} = {@code rate} unidades de {@code quote}.
 * La conversión redondea HALF_EVEN a los dígitos por defecto de la moneda destino
 * (garantizado por el constructor de {@link Money}).
 */
public record FxQuote(
        Currency base,
        Currency quote,
        BigDecimal rate,
        String source,
        Instant fetchedAt
) {

    public FxQuote {
        Objects.requireNonNull(base, "base es requerido");
        Objects.requireNonNull(quote, "quote es requerido");
        Objects.requireNonNull(rate, "rate es requerido");
        if (rate.signum() <= 0) {
            throw new BusinessRuleViolationException("FX_RATE_INVALID",
                    "El tipo de cambio debe ser > 0, recibido " + rate.toPlainString());
        }
        if (fetchedAt == null) {
            fetchedAt = Instant.now();
        }
    }

    /**
     * Convierte {@code amount} (que debe estar en {@code base}) a {@code quote},
     * redondeando HALF_EVEN a los dígitos de la moneda destino.
     */
    public Money convert(Money amount) {
        Objects.requireNonNull(amount, "amount es requerido");
        if (!amount.currency().equals(base)) {
            throw new BusinessRuleViolationException("FX_QUOTE_CURRENCY_MISMATCH",
                    "La cotización es %s→%s pero el monto está en %s"
                            .formatted(base, quote, amount.currency()));
        }
        BigDecimal converted = amount.amount().multiply(rate)
                .setScale(quote.defaultFractionDigits(), RoundingMode.HALF_EVEN);
        return Money.of(converted, quote);
    }
}
