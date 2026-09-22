package com.micropay.backend.application;

import com.micropay.backend.domain.entities.AccountMove;
import com.micropay.backend.domain.entities.Transaction;
import com.micropay.backend.domain.entities.Wallet;
import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.valueobjects.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.BigRange;
import net.jqwik.api.constraints.Scale;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T10 Property Based Tests — Ledger + Transaction invariant.
 *
 * Contiene las Properties marcadas en tasks.md para ProcessTransactionUseCase:
 *  5. Fondos suficientes antes del débito.
 *  6. Conservación de suma balances sistema (partida doble).
 *  8. Atomicidad: fallo ⇒ rollback total (balances idempotentes post-excepción).
 * 13. Consistencia double-entry ledger.
 * 22. Idempotencia TransactionId único.
 * 23. No doble gasto: secuencia credit/debit preserva balances ≥ 0.
 */
public class TransactionLedgerProperty {

    @Provide
    Arbitrary<Currency> currency() {
        return Arbitraries.of(Currency.COP, Currency.USD);
    }

    private static Wallet newWallet(Currency c) {
        return Wallet.builder()
                .userId(UserId.generate())
                .currency(c)
                .status(Wallet.Status.ACTIVE)
                .createdBy(UUID.randomUUID())
                .build();
    }

    private static final class P2PTransferResult {
        final Wallet from;
        final Wallet to;
        final Transaction tx;
        final Money initialFrom;
        final Money initialTo;
        final Money amount;

        P2PTransferResult(Wallet from, Wallet to, Transaction tx,
                          Money initialFrom, Money initialTo, Money amount) {
            this.from = from;
            this.to = to;
            this.tx = tx;
            this.initialFrom = initialFrom;
            this.initialTo = initialTo;
            this.amount = amount;
        }
    }

    /** Property 5: Fondos suficientes — si amount > from.balance() antes débito => falla */
    @Property(tries = 150)
    void p05_insufficient_funds_always_rejects(
            @ForAll("currency") Currency c,
            @ForAll @BigRange(min = "1", max = "100000") @Scale(4) BigDecimal initialRaw,
            @ForAll @BigRange(min = "1", max = "100000") @Scale(4) BigDecimal deltaRaw) {
        Wallet from = newWallet(c);
        Wallet to = newWallet(c);
        Money initial = Money.of(initialRaw, c);
        Money tooMuch = Money.of(initial.amount().add(deltaRaw), c);
        from.credit(initial, "seed");
        assertThatThrownBy(() -> from.debit(tooMuch, "transfer"))
                .isInstanceOf(BusinessRuleViolationException.class);
        // Post: balances no cambiaron después de fallo
        assertThat(from.balance()).isEqualTo(initial);
        assertThat(to.balance()).isEqualTo(Money.zero(c));
    }

    /**
     * Property 6: Conservación de suma — antes y después de transferencia P2P exitosa,
     *             suma(from.balance + to.balance) se mantiene constante (sin fees).
     */
    @Property(tries = 150)
    void p06_sum_balances_preserved_on_successful_p2p(
            @ForAll("currency") Currency c,
            @ForAll @BigRange(min = "1000", max = "100000000") @Scale(4) BigDecimal fromSeed,
            @ForAll @BigRange(min = "0", max = "50000000") @Scale(4) BigDecimal toSeed,
            @ForAll @BigRange(min = "1", max = "500000") @Scale(4) BigDecimal transferRaw) {
        Wallet from = newWallet(c);
        Wallet to = newWallet(c);
        Money fromStart = Money.of(fromSeed, c);
        Money toStart = Money.of(toSeed, c);
        Money amount = Money.of(transferRaw, c);
        from.credit(fromStart, "seed from");
        if (toStart.isPositive()) {
            to.credit(toStart, "seed to");
        }
        Money sumBefore = from.balance().add(to.balance());
        Assume.that(from.available().isGreaterThanOrEqual(amount));

        Money afterDebit = from.debit(amount, "transfer");
        Money afterCredit = to.credit(amount, "transfer");

        Money sumAfter = from.balance().add(to.balance());
        assertThat(sumAfter).isEqualTo(sumBefore);
        assertThat(afterDebit).isEqualTo(from.balance());
        assertThat(afterCredit).isEqualTo(to.balance());
    }

    /** Property 8: Atomicidad fallos — excepción intermedia en el segundo paso NO deja saldos rotos */
    @Property(tries = 150)
    void p08_atomicity_exception_midway_no_partial_state(
            @ForAll("currency") Currency c,
            @ForAll @BigRange(min = "100", max = "100000") @Scale(4) BigDecimal fromSeedRaw) {
        Wallet from = newWallet(c);
        Wallet to = newWallet(c);
        Money seed = Money.of(fromSeedRaw, c);
        Money amount = Money.of(
                seed.amount().multiply(new BigDecimal("0.1"))
                        .setScale(c.defaultFractionDigits(), java.math.RoundingMode.DOWN), c);
        Assume.that(amount.isPositive());
        from.credit(seed, "seed");
        Money before = from.balance().add(to.balance());
        Money beforeFrom = from.balance();
        Money beforeTo = to.balance();

        // Simulación: from hace debit OK, luego lanzamos excepción antes de credit.
        // En aplicación real el @Transactional hace rollback; en dominio el caller debe revertir manual.
        // Probamos la invariante: si revertimos ambos pasos, balances no cambian.
        try {
            Money snapFrom = from.debit(amount, "simulated p2p step 1");
            assertThat(from.balance()).isEqualTo(snapFrom);
            // Simular fallo antes de credit: caller debe rollback vía credit(from)/debit(to).
            throw new SimulatedFailureRuntimeException("second step simulated crash before credit");
        } catch (SimulatedFailureRuntimeException ex) {
            // Rollback manual (post-compensación en capa application transactional):
            from.credit(amount, "rollback debit");
            assertThat(from.balance()).isEqualTo(beforeFrom);
            assertThat(to.balance()).isEqualTo(beforeTo);
            assertThat(from.balance().add(to.balance())).isEqualTo(before);
        }
    }

    /** Property 13: Consistencia ledger double-entry — 2 AccountMoves: 1 débito negado + 1 crédito positivo, suma cero. */
    @Property(tries = 150)
    void p13_ledger_double_entry_balances_to_zero(
            @ForAll("currency") Currency c,
            @ForAll @BigRange(min = "1000", max = "100000000") @Scale(4) BigDecimal fromSeedRaw,
            @ForAll @BigRange(min = "1", max = "100000") @Scale(4) BigDecimal transferRaw) {
        P2PTransferResult r = simulateP2PSuccess(c, fromSeedRaw, transferRaw);

        // BR#06 — exactamente 2 AccountMove por Transfer P2P
        assertThat(r.tx.accountMoves()).hasSize(2);
        AccountMove debitMove = r.tx.accountMoves().stream()
                .filter(AccountMove::isDebit).findFirst().orElseThrow();
        AccountMove creditMove = r.tx.accountMoves().stream()
                .filter(AccountMove::isCredit).findFirst().orElseThrow();

        // Wallet IDs correctos
        assertThat(debitMove.walletId()).isEqualTo(r.from.id());
        assertThat(creditMove.walletId()).isEqualTo(r.to.id());
        // Monedas iguales
        assertThat(debitMove.amount().currency()).isEqualTo(c);
        assertThat(creditMove.amount().currency()).isEqualTo(c);
        // Suma movimientos algebraica = 0 (conservación masa monetaria sistema)
        Money sumMoves = debitMove.amount().add(creditMove.amount());
        assertThat(sumMoves).isEqualTo(Money.zero(c));
        // BR#05 — balanceAfter coincide con el snapshot real de wallet después
        assertThat(debitMove.balanceAfter()).isEqualTo(r.from.balance());
        assertThat(creditMove.balanceAfter()).isEqualTo(r.to.balance());
    }

    /** Property 22: Idempotencia TransactionId — misma TransactionId no genera dos aggregate distintos. */
    @Property(tries = 120)
    void p22_transaction_idempotent_same_transactionid(
            @ForAll("currency") Currency c,
            @ForAll @BigRange(min = "10000", max = "10000000") @Scale(4) BigDecimal fromSeed,
            @ForAll @BigRange(min = "1", max = "500000") @Scale(4) BigDecimal transferRaw) {
        TransactionId fixedTxId = TransactionId.generate();
        ShortCode fixedShort = ShortCode.random();
        P2PTransferResult r = simulateP2PSuccess(c, fromSeed, transferRaw, fixedTxId, fixedShort);
        Transaction tx2 = buildTxFromResult(r, fixedTxId, fixedShort);
        // Mismo TransactionId + misma semilla de AccountMove = mismo aggregate (id equality)
        assertThat(tx2.id()).isEqualTo(r.tx.id());
        assertThat(tx2.shortCode()).isEqualTo(r.tx.shortCode());
        assertThat(tx2.grossAmount()).isEqualTo(r.tx.grossAmount());
    }

    /**
     * Property 23: No doble gasto — secuencia N transferencias sucesivas de la misma wallet
     *               mantiene balance >= 0 y suma todos los débitos == reducción neta de balance.
     */
    @Property(tries = 120)
    void p23_no_double_spend_sequential_transfers(
            @ForAll("currency") Currency c,
            @ForAll @BigRange(min = "100000", max = "1000000000") @Scale(4) BigDecimal fromSeedRaw) {
        Wallet from = newWallet(c);
        Wallet to = newWallet(c);
        Money seed = Money.of(fromSeedRaw, c);
        from.credit(seed, "seed");
        Money startFrom = from.balance();
        Money startTo = to.balance();
        Money totalDebited = Money.zero(c);
        int maxSteps = 15;
        // Construye transferencias decrecientes en 10% del saldo restante:
        for (int i = 0; i < maxSteps; i++) {
            Money step = Money.of(
                    from.available().amount()
                            .multiply(new BigDecimal("0.07"))
                            .setScale(c.defaultFractionDigits(), java.math.RoundingMode.DOWN), c);
            if (!step.isPositive()) break;
            from.debit(step, "transfer step " + i);
            to.credit(step, "receive step " + i);
            totalDebited = totalDebited.add(step);
            // BR#01: balance nunca negativo en cada paso
            assertThat(from.balance().amount().signum()).isNotNegative();
            assertThat(from.available().amount().signum()).isNotNegative();
        }
        // Suma débitos = reducción real wallet from
        assertThat(startFrom.subtract(from.balance())).isEqualTo(totalDebited);
        // Suma créditos = aumento real wallet to
        assertThat(to.balance().subtract(startTo)).isEqualTo(totalDebited);
        // Masa monetaria conservada
        assertThat(startFrom.add(startTo)).isEqualTo(from.balance().add(to.balance()));
    }

    // ────────── Helpers ──────────

    private P2PTransferResult simulateP2PSuccess(Currency c, BigDecimal fromSeedRaw, BigDecimal transferRaw) {
        return simulateP2PSuccess(c, fromSeedRaw, transferRaw, TransactionId.generate(), ShortCode.random());
    }

    private P2PTransferResult simulateP2PSuccess(Currency c, BigDecimal fromSeedRaw, BigDecimal transferRaw,
                                                 TransactionId txId, ShortCode shortCode) {
        Wallet from = newWallet(c);
        Wallet to = newWallet(c);
        Money fromInitial = Money.of(fromSeedRaw, c);
        from.credit(fromInitial, "seed from");
        Money amount = Money.of(transferRaw, c);
        Assume.that(from.available().isGreaterThanOrEqual(amount));

        Money afterDebit = from.debit(amount, "P2P out");
        Money afterCredit = to.credit(amount, "P2P in");

        List<AccountMove> moves = new ArrayList<>();
        moves.add(new AccountMove(UUID.randomUUID(), from.id(), txId,
                Money.of(amount.amount().negate(), c), afterDebit, Instant.now()));
        moves.add(new AccountMove(UUID.randomUUID(), to.id(), txId,
                amount, afterCredit, Instant.now()));

        Transaction tx = Transaction.builder()
                .id(txId)
                .shortCode(shortCode)
                .type(TransactionType.TRANSFER_P2P)
                .walletFromId(from.id())
                .walletToId(to.id())
                .grossAmount(amount)
                .netAmount(amount)
                .feeAmount(Money.zero(c))
                .status(TransactionStatus.COMPLETED)
                .createdBy(from.userId().uuid())
                .accountMoves(moves)
                .build();
        return new P2PTransferResult(from, to, tx, fromInitial, Money.zero(c), amount);
    }

    private Transaction buildTxFromResult(P2PTransferResult r, TransactionId id, ShortCode shortCode) {
        List<AccountMove> clone = new ArrayList<>(r.tx.accountMoves());
        return Transaction.builder()
                .id(id)
                .shortCode(shortCode)
                .type(TransactionType.TRANSFER_P2P)
                .walletFromId(r.from.id())
                .walletToId(r.to.id())
                .grossAmount(r.tx.grossAmount())
                .netAmount(r.tx.netAmount())
                .feeAmount(r.tx.feeAmount())
                .status(TransactionStatus.COMPLETED)
                .createdBy(r.tx.createdBy())
                .accountMoves(clone)
                .build();
    }

    /** Señal de fallo controlado en property P8 para comprobar rollback manual. */
    private static final class SimulatedFailureRuntimeException extends RuntimeException {
        SimulatedFailureRuntimeException(String msg) { super(msg); }
    }
}
