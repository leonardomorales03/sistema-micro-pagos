package com.micropay.backend.domain.valueobjects;

import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.exceptions.InvalidValueObjectException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Money — Value Object con reglas monetarias estrictas.
 * <p>
 * Reglas invariables (Domain Rule):
 * 1. amount NUNCA es null
 * 2. amount siempre se escala a `currency.defaultFractionDigits` con HALF_EVEN
 * 3. operaciones requieren misma Currency (sin conversión implícita — usar FX Service)
 * 4. para transfer/topup — amount > 0
 * 5. representación interna: BigDecimal con MathContext DECIMAL64 para precision/velocidad
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount es requerido");
        Objects.requireNonNull(currency, "currency es requerido");

        if (amount.scale() > 18) {
            throw new InvalidValueObjectException("Money.amount",
                    "escala excede el máximo permitido (18), recibida " + amount.scale());
        }

        amount = amount.setScale(currency.defaultFractionDigits(), RoundingMode.HALF_EVEN);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public static Money of(long unscaled, Currency currency) {
        return new Money(BigDecimal.valueOf(unscaled, currency.defaultFractionDigits()), currency);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    /** Suma. Misma moneda requerida — NO conversión implícita. */
    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    /** Resta. Misma moneda requerida. */
    public Money subtract(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public Money multiply(BigDecimal factor) {
        return new Money(this.amount.multiply(factor), this.currency);
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isGreaterThan(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isGreaterThanOrEqual(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) >= 0;
    }

    public boolean isLessThan(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) < 0;
    }

    private void assertSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new BusinessRuleViolationException("MONEY_CURRENCY_MISMATCH",
                    "Monedas no coinciden: %s vs %s".formatted(this.currency, other.currency));
        }
    }

    /** Escala fija por moneda. Usar en compraciones equals */
    public BigDecimal scaledAmount() {
        return amount.setScale(currency.defaultFractionDigits(), RoundingMode.HALF_EVEN);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money money)) return false;
        return currency == money.currency && scaledAmount().equals(money.scaledAmount());
    }

    @Override
    public int hashCode() {
        return Objects.hash(scaledAmount(), currency);
    }

    @Override
    public String toString() {
        return "%s %s".formatted(currency.symbol(), amount.toPlainString());
    }
}
