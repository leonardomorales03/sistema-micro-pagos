package com.micropay.backend.domain.entities;

import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.valueobjects.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.BigRange;
import net.jqwik.api.constraints.Scale;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class WalletAggregateProperty {

    // ────────── Providers ──────────

    @Provide
    Arbitrary<Currency> anyCurrency() {
        return Arbitraries.of(Currency.values());
    }

    @Provide
    Arbitrary<UserId> anyUserId() {
        return Arbitraries.randomValue(rng -> UserId.generate());
    }

    @Provide
    Arbitrary<Wallet> activeWalletArbitrary() {
        return Combinators.combine(anyUserId(), anyCurrency()).as((uid, c) ->
                Wallet.builder().userId(uid).currency(c).status(Wallet.Status.ACTIVE).createdBy(UUID.randomUUID()).build());
    }

    /** Wallet activa con balance entre 1 y 1M unidades de su moneda */
    @Provide
    Arbitrary<Wallet> fundedWallet() {
        return activeWalletArbitrary().flatMap(w -> {
            Currency c = w.currency();
            int maxUnits = c == Currency.COP ? 1_000_000 : 500_000;
            return Arbitraries.bigDecimals()
                    .between(BigDecimal.valueOf(1), BigDecimal.valueOf(maxUnits))
                    .ofScale(Math.min(c.defaultFractionDigits(), 6))
                    .map(amount -> {
                        Money credit = Money.of(amount, c);
                        w.credit(credit, "seedFundedWallet");
                        return w;
                    });
        });
    }

    @Provide
    Arbitrary<Money> positiveMoneyForWalletCurrency() {
        return fundedWallet().flatMap(w -> positiveMoneyOfCurrency(w.currency()));
    }

    private Arbitrary<Money> positiveMoneyOfCurrency(Currency currency) {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(1), BigDecimal.valueOf(currency == Currency.COP ? 1_000_000 : 500_000))
                .ofScale(Math.min(currency.defaultFractionDigits(), 6))
                .map(a -> Money.of(a, currency));
    }

    @Provide
    Arbitrary<Tuple.Tuple3<Wallet, Wallet, Money>> walletPairAndUnitSameCurrency() {
        return anyCurrency().flatMap(c ->
                Combinators.combine(
                        activeWalletArbitrary().map(w -> w.currency() == c ? w :
                                Wallet.builder().userId(w.userId()).currency(c)
                                        .status(w.status()).createdBy(w.createdBy() == null ? UUID.randomUUID() : w.createdBy()).build()),
                        activeWalletArbitrary().map(w -> w.currency() == c ? w :
                                Wallet.builder().userId(w.userId()).currency(c)
                                        .status(w.status()).createdBy(w.createdBy() == null ? UUID.randomUUID() : w.createdBy()).build()),
                        positiveMoneyOfCurrency(c)
                ).as(Tuple::of));
    }

    // ────────── Properties ──────────

    /** Property 1: Balance inicial cero — createFor inicializa balance y reserved en zero */
    @Property(tries = 150)
    void p01_balance_initial_zero(@ForAll("anyUserId") UserId uid,
                                  @ForAll("anyCurrency") Currency currency) {
        Wallet w = Wallet.createFor(uid, currency, UUID.randomUUID());
        assertThat(w.balance()).isEqualTo(Money.zero(currency));
        assertThat(w.reserved()).isEqualTo(Money.zero(currency));
        assertThat(w.available()).isEqualTo(Money.zero(currency));
        assertThat(w.status()).isEqualTo(Wallet.Status.ACTIVE);
        assertThat(w.version()).isZero();
    }

    /** Property 9: Prevención de saldos negativos — cualquier debit excedente lanza INSUFFICIENT_AVAILABLE_BALANCE */
    @Property(tries = 150)
    void p09_prevent_negative_balance_debit_more_than_available(@ForAll("fundedWallet") Wallet w) {
        Money plusOne = Money.of(w.available().amount().add(BigDecimal.ONE), w.currency());
        assertThatThrownBy(() -> w.debit(plusOne, "p09 debit"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting(ex -> ((BusinessRuleViolationException) ex).getCode())
                .matches(code -> String.valueOf(code).contains("INSUFFICIENT")
                        || String.valueOf(code).contains("WALLET_NEGATIVE"));
        // Post: balance no mutó después de fallo
        assertThat(w.available().amount().signum()).isNotNegative();
    }

    /** Property: credit/debit mismo amount cancelación — balance inalterado */
    @Property(tries = 150)
    void p_credit_then_debit_same_amount_preserves_balance(
            @ForAll("fundedWallet") Wallet w,
            @ForAll("positiveMoneyForWalletCurrency") Money anyAmount) {
        Assume.that(anyAmount.currency() == w.currency());
        Money before = w.balance();
        w.credit(anyAmount, "p credit");
        w.debit(anyAmount, "p debit");
        assertThat(w.balance()).isEqualTo(before);
    }

    /** Property: amount NO positivo en credit/debit lanza AMOUNT_NOT_POSITIVE */
    @Property(tries = 120)
    void p_amount_non_positive_throws(@ForAll("activeWalletArbitrary") Wallet w,
                                      @ForAll @BigRange(min = "-1000", max = "0") @Scale(4) BigDecimal nonPositiveRaw) {
        Currency c = w.currency();
        Money amount = Money.of(nonPositiveRaw, c);
        Assume.that(!amount.isPositive());
        assertThatThrownBy(() -> w.credit(amount, "nonPos credit"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting(ex -> ((BusinessRuleViolationException) ex).getCode())
                .asString().contains("AMOUNT_NOT_POSITIVE");
        assertThatThrownBy(() -> w.debit(amount, "nonPos debit"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    /** Property: currency mismatch lanza CURRENCY_MISMATCH siempre */
    @Property(tries = 100)
    void p_currency_mismatch_throws(@ForAll Currency walletCurrency, @ForAll Currency otherCurrency) {
        Assume.that(walletCurrency != otherCurrency);
        UserId uid = UserId.generate();
        Wallet w = Wallet.createFor(uid, walletCurrency, UUID.randomUUID());
        Money other = Money.of(BigDecimal.ONE, otherCurrency);
        assertThatThrownBy(() -> w.credit(other, "mismatch credit"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting(ex -> ((BusinessRuleViolationException) ex).getCode())
                .asString().contains("CURRENCY_MISMATCH");
        assertThatThrownBy(() -> w.debit(other, "mismatch debit"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    /** Property: Wallet FROZEN lanza WALLET_NOT_ACTIVE en credit/debit; crédito NO se aplicó */
    @Property(tries = 120)
    void p_frozen_status_prevents_operations(@ForAll("fundedWallet") Wallet w) {
        Money before = w.balance();
        w.freeze();
        Money amount = Money.of(BigDecimal.ONE, w.currency());
        assertThatThrownBy(() -> w.credit(amount, "frozen credit"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting(ex -> ((BusinessRuleViolationException) ex).getCode())
                .asString().contains("WALLET_NOT_ACTIVE");
        assertThat(w.balance()).isEqualTo(before);
        w.activate();
        w.credit(amount, "unfrozen credit");
        assertThat(w.balance().amount()).isGreaterThan(before.amount());
    }

    /** Property: credit N veces de X = credit(n*X) — idempotencia aditiva */
    @Property(tries = 150)
    void p_credit_n_times_equals_batch(
            @ForAll("walletPairAndUnitSameCurrency") Tuple.Tuple3<Wallet, Wallet, Money> triple) {
        Wallet w1 = triple.get1();
        Wallet w2 = triple.get2();
        Money unit = triple.get3();
        int n = 10;
        for (int i = 0; i < n; i++) {
            w1.credit(unit, "unit credit " + i);
        }
        Money batch = Money.of(unit.amount().multiply(BigDecimal.valueOf(n)), unit.currency());
        w2.credit(batch, "batch credit");
        assertThat(w1.balance()).isEqualTo(w2.balance());
    }

    /** Property: available = balance - reserved — invariante después de reserve/release */
    @Property(tries = 150)
    void p_available_invariant_after_reserve_and_release(@ForAll("fundedWallet") Wallet w) {
        Currency c = w.currency();
        Money balBefore = w.balance();
        Money half = Money.of(balBefore.amount()
                .divide(BigDecimal.valueOf(2), c.defaultFractionDigits(), java.math.RoundingMode.DOWN), c);
        Assume.that(half.isPositive());
        Money availBefore = w.available();
        w.reserve(half, "hold op");
        assertThat(w.available()).isEqualTo(availBefore.subtract(half));
        assertThat(w.balance()).isEqualTo(balBefore);
        w.releaseReserve(half, "release op");
        assertThat(w.available()).isEqualTo(availBefore);
        assertThat(w.balance()).isEqualTo(balBefore);
    }

    /** Property: confirmReserveAsDebit mueve balance y reserva sin tocar available extra */
    @Property(tries = 150)
    void p_confirm_reserve_matches_net_debit(@ForAll("fundedWallet") Wallet w) {
        Currency c = w.currency();
        Money balBefore = w.balance();
        Money availBefore = w.available();
        Money reserveAmount = Money.of(
                balBefore.amount().multiply(new BigDecimal("0.25"))
                        .setScale(c.defaultFractionDigits(), java.math.RoundingMode.DOWN), c);
        Assume.that(reserveAmount.isPositive());
        w.reserve(reserveAmount, "hold");
        w.confirmReserveAsDebit(reserveAmount, "confirm");
        assertThat(w.balance()).isEqualTo(balBefore.subtract(reserveAmount));
        assertThat(w.reserved()).isEqualTo(Money.zero(c));
        assertThat(w.available()).isEqualTo(availBefore.subtract(reserveAmount));
    }

    /** Property: over-release (releaseReserve > reserved) siempre lanza WALLET_RESERVE_OVERRELEASE */
    @Property(tries = 120)
    void p_over_release_throws(@ForAll("fundedWallet") Wallet w) {
        Currency c = w.currency();
        Money balHalf = Money.of(w.balance().amount()
                .divide(BigDecimal.valueOf(2), c.defaultFractionDigits(), java.math.RoundingMode.DOWN), c);
        Assume.that(balHalf.isPositive());
        w.reserve(balHalf, "hold");
        Money over = balHalf.add(Money.of(BigDecimal.ONE, c));
        assertThatThrownBy(() -> w.releaseReserve(over, "over release"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting(ex -> ((BusinessRuleViolationException) ex).getCode())
                .asString().contains("WALLET_RESERVE_OVERRELEASE");
    }
}
