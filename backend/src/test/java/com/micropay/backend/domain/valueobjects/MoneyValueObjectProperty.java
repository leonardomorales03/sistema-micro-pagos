package com.micropay.backend.domain.valueobjects;

import net.jqwik.api.*;
import net.jqwik.api.constraints.BigRange;
import net.jqwik.api.constraints.Scale;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MoneyValueObjectProperty {

    private static final Arbitrary<Currency> CURRENCIES = Arbitraries.of(Currency.values());

    private static Arbitrary<Money> moneyForCurrency(Currency currency) {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(-999_999_999_999L), BigDecimal.valueOf(999_999_999_999L))
                .ofScale(Math.min(currency.defaultFractionDigits(), 8))
                .map(amount -> Money.of(amount, currency));
    }

    @Provide
    Arbitrary<Tuple.Tuple2<Money, Money>> sameCurrencyPairs() {
        return CURRENCIES.flatMap(currency ->
                Combinators.combine(moneyForCurrency(currency), moneyForCurrency(currency)).as(Tuple::of));
    }

    @Provide
    Arbitrary<Tuple.Tuple3<Money, Money, Money>> sameCurrencyTriples() {
        return CURRENCIES.flatMap(currency ->
                Combinators.combine(
                        moneyForCurrency(currency),
                        moneyForCurrency(currency),
                        moneyForCurrency(currency)
                ).as(Tuple::of));
    }

    // ────────── Properties 1-4: Axiomas Value Object ──────────

    /** Property: escala fija por moneda — constructor normaliza a currency.defaultFractionDigits() */
    @Property(tries = 150)
    void p01_money_scale_matches_currency_defaultFractionDigits(
            @ForAll("currenciesFromProvider") Currency currency,
            @ForAll @BigRange(min = "-999999999999", max = "999999999999") @Scale(10) BigDecimal rawAmount) {
        Money m = Money.of(rawAmount, currency);
        assertThat(m.amount().scale()).isEqualTo(currency.defaultFractionDigits());
    }

    @Provide
    Arbitrary<Currency> currenciesFromProvider() {
        return CURRENCIES;
    }

    /** Property: equals por valor escalado — same numeric value ⇒ equals + same hashCode */
    @Property(tries = 150)
    void p02_money_equals_by_scaled_value(
            @ForAll("currenciesFromProvider") Currency currency,
            @ForAll @BigRange(min = "0", max = "100000000") @Scale(8) BigDecimal v) {
        BigDecimal scaled = v.setScale(currency.defaultFractionDigits(), RoundingMode.HALF_EVEN);
        BigDecimal stripped = scaled.stripTrailingZeros();
        Money a = Money.of(scaled, currency);
        Money b = Money.of(stripped, currency);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    /** Property: add — conmutativa (a + b) == (b + a) */
    @Property(tries = 150)
    void p03_add_commutative(@ForAll("sameCurrencyPairs") Tuple.Tuple2<Money, Money> pair) {
        Money a = pair.get1();
        Money b = pair.get2();
        assertThat(a.add(b)).isEqualTo(b.add(a));
    }

    /** Property: add — asociativa (a + b) + c == a + (b + c) */
    @Property(tries = 150)
    void p04_add_associative(@ForAll("sameCurrencyTriples") Tuple.Tuple3<Money, Money, Money> triple) {
        Money a = triple.get1();
        Money b = triple.get2();
        Money c = triple.get3();
        Money left = a.add(b).add(c);
        Money right = a.add(b.add(c));
        assertThat(left).isEqualTo(right);
    }

    /** Property: add cero identidad a + 0 == a */
    @Property(tries = 150)
    void p05_add_zero_identity(@ForAll("currenciesFromProvider") Currency currency,
                               @ForAll @BigRange(min = "-100000000", max = "100000000") @Scale(8) BigDecimal amount) {
        Money a = Money.of(amount, currency);
        Money zero = Money.zero(currency);
        assertThat(a.add(zero)).isEqualTo(a);
        assertThat(zero.add(a)).isEqualTo(a);
    }

    /** Property: subtract cero identidad a - 0 == a */
    @Property(tries = 150)
    void p06_subtract_zero_identity(@ForAll("sameCurrencyPairs") Tuple.Tuple2<Money, Money> pair) {
        Money a = pair.get1();
        Money zero = Money.zero(a.currency());
        assertThat(a.subtract(zero)).isEqualTo(a);
    }

    /** Property: (a + b) - b == a (inverso exacto escala moneda) */
    @Property(tries = 150)
    void p07_add_then_subtract_same_value(@ForAll("sameCurrencyPairs") Tuple.Tuple2<Money, Money> pair) {
        Money a = pair.get1();
        Money b = pair.get2();
        assertThat(a.add(b).subtract(b)).isEqualTo(a);
    }

    /** Property: isPositive <=> amount.signum() > 0 y isPositive excluye cero */
    @Property(tries = 150)
    void p08_isPositive_isZero_isNegative_exhaustive(
            @ForAll("currenciesFromProvider") Currency currency,
            @ForAll @BigRange(min = "-100000", max = "100000") @Scale(8) BigDecimal v) {
        Money m = Money.of(v, currency);
        int signum = m.amount().signum();
        if (signum > 0) {
            assertThat(m.isPositive()).isTrue();
            assertThat(m.isZero()).isFalse();
            assertThat(m.isNegative()).isFalse();
        } else if (signum < 0) {
            assertThat(m.isNegative()).isTrue();
            assertThat(m.isZero()).isFalse();
            assertThat(m.isPositive()).isFalse();
        } else {
            assertThat(m.isZero()).isTrue();
            assertThat(m.isPositive()).isFalse();
            assertThat(m.isNegative()).isFalse();
        }
    }

    /** Property: currency mismatch add/subtract ⇒ BusinessRuleViolation code MONEY_CURRENCY_MISMATCH */
    @Property(tries = 100)
    void p09_currency_mismatch_throws(@ForAll Currency c1, @ForAll Currency c2) {
        Assume.that(c1 != c2);
        Money a = Money.of(BigDecimal.ONE, c1);
        Money b = Money.of(BigDecimal.ONE, c2);
        assertThatThrownBy(() -> a.add(b))
                .isInstanceOf(com.micropay.backend.domain.exceptions.BusinessRuleViolationException.class)
                .hasMessageContaining("Monedas no coinciden");
        assertThatThrownBy(() -> a.subtract(b))
                .isInstanceOf(com.micropay.backend.domain.exceptions.BusinessRuleViolationException.class);
    }

    /** Property: Money.zero(currency) ⇒ isZero y escala correcta */
    @Property(tries = 80)
    void p10_zero_factory_is_zero_with_correct_scale(@ForAll("currenciesFromProvider") Currency currency) {
        Money zero = Money.zero(currency);
        assertThat(zero.isZero()).isTrue();
        assertThat(zero.currency()).isEqualTo(currency);
        assertThat(zero.amount().scale()).isEqualTo(currency.defaultFractionDigits());
    }

    /** Property: Money.of(unscaled, currency) roundtrip — amount.unscaledValue ≡ unscaled tras escala */
    @Property(tries = 150)
    void p11_of_unscaled_roundtrip(
            @ForAll("currenciesFromProvider") Currency currency,
            @ForAll("nonNegativeLongs") long unscaled) {
        Money m = Money.of(unscaled, currency);
        int scale = currency.defaultFractionDigits();
        BigDecimal expected = BigDecimal.valueOf(unscaled, scale).setScale(scale, RoundingMode.HALF_EVEN);
        assertThat(m.amount()).isEqualByComparingTo(expected);
    }

    @Provide
    Arbitrary<Long> nonNegativeLongs() {
        return Arbitraries.longs().between(0L, 999_999_999_999L);
    }

    /** Property: orden total isGreaterThan >, isLessThan <, isEqual transitivo */
    @Property(tries = 150)
    void p12_ordering_total_same_currency(@ForAll("sameCurrencyTriples") Tuple.Tuple3<Money, Money, Money> triple) {
        Money a = triple.get1();
        Money b = triple.get2();
        Money c = triple.get3();
        int cmpAB = a.amount().compareTo(b.amount());
        int cmpBC = b.amount().compareTo(c.amount());
        if (cmpAB > 0 && cmpBC > 0) {
            assertThat(a.isGreaterThan(b)).isTrue();
            assertThat(b.isGreaterThan(c)).isTrue();
            assertThat(a.isGreaterThan(c)).isTrue();
        } else if (cmpAB < 0 && cmpBC < 0) {
            assertThat(a.isLessThan(b)).isTrue();
            assertThat(b.isLessThan(c)).isTrue();
            assertThat(a.isLessThan(c)).isTrue();
        } else if (cmpAB == 0) {
            assertThat(a.isGreaterThanOrEqual(b)).isTrue();
            assertThat(b.isGreaterThanOrEqual(a)).isTrue();
        }
    }

    /** Property: escala excesiva (>18) lanza InvalidValueObjectException siempre */
    @Property(tries = 100)
    void p13_scale_overflow_invalid(
            @ForAll("currenciesFromProvider") Currency currency,
            @ForAll("overScaledBigDecimal") BigDecimal overScaled) {
        assertThatThrownBy(() -> Money.of(overScaled, currency))
                .isInstanceOf(com.micropay.backend.domain.exceptions.InvalidValueObjectException.class)
                .hasMessageContaining("escala excede el máximo permitido");
    }

    @Provide
    Arbitrary<BigDecimal> overScaledBigDecimal() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(-1), BigDecimal.ONE)
                .ofScale(25);
    }

    /** Property: multiply por 1 = identidad, por 0 = zero, negación multiplicando * (-1) */
    @Property(tries = 150)
    void p14_multiply_identity_zero_negation(
            @ForAll("currenciesFromProvider") Currency currency,
            @ForAll @BigRange(min = "-1000000", max = "1000000") @Scale(6) BigDecimal amount,
            @ForAll @BigRange(min = "-10", max = "10") @Scale(4) BigDecimal factor) {
        Money base = Money.of(amount, currency);
        Money timesOne = base.multiply(BigDecimal.ONE);
        assertThat(timesOne).isEqualTo(base);
        Money timesZero = base.multiply(BigDecimal.ZERO);
        assertThat(timesZero).isEqualTo(Money.zero(currency));
    }
}
