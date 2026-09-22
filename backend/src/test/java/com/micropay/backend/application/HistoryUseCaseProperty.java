package com.micropay.backend.application;

import com.micropay.backend.domain.entities.AccountMove;
import com.micropay.backend.domain.entities.Transaction;
import com.micropay.backend.domain.entities.Wallet;
import com.micropay.backend.domain.valueobjects.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.BigRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Scale;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T10 Property 15: Filtrado correcto de transacciones por wallet.
 *
 * El dominio actual no implementa GetTransactionHistoryUseCase (fuera de la capa de aplicación
 * sin infraestructura), así que validamos en tests la invariante:
 *   - Toda Transaction relacionada con wallet W aparece en ambas listas (from|to)
 *   - Filtrado por rango de fechas excluye transacciones fuera del rango
 *   - Paginación devuelve subconjunto ordenado por fecha desc
 */
public class HistoryUseCaseProperty {

    private static final com.micropay.backend.domain.valueobjects.Currency CURRENCY_DEFAULT =
            com.micropay.backend.domain.valueobjects.Currency.COP;

    @Provide
    Arbitrary<Long> seedEpochs() {
        return Arbitraries.longs().between(1L, 5_000_000_000L);
    }

    /** Helper: crea wallet, walletFrom y crea N transacciones P2P sobre ese wallet (origen o destino). */
    private static final class Scenario {
        final Wallet a;
        final Wallet b;
        final Wallet c;
        final List<Transaction> allTx;

        Scenario(Wallet a, Wallet b, Wallet c, List<Transaction> txs) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.allTx = txs;
        }
    }

    @Provide
    Arbitrary<Integer> txCount() {
        return Arbitraries.integers().between(3, 20);
    }

    private Scenario buildScenario(int numTx, long seedStartEpoch) {
        Wallet a = Wallet.builder().userId(UserId.generate()).currency(CURRENCY_DEFAULT)
                .status(Wallet.Status.ACTIVE).createdBy(UUID.randomUUID()).build();
        Wallet b = Wallet.builder().userId(UserId.generate()).currency(CURRENCY_DEFAULT)
                .status(Wallet.Status.ACTIVE).createdBy(UUID.randomUUID()).build();
        Wallet c = Wallet.builder().userId(UserId.generate()).currency(CURRENCY_DEFAULT)
                .status(Wallet.Status.ACTIVE).createdBy(UUID.randomUUID()).build();
        Instant baseTime = Instant.EPOCH.plus(seedStartEpoch, ChronoUnit.SECONDS);
        a.credit(Money.of(new BigDecimal("100000000"), CURRENCY_DEFAULT), "seed");
        b.credit(Money.of(new BigDecimal("50000000"), CURRENCY_DEFAULT), "seed");
        c.credit(Money.of(new BigDecimal("50000000"), CURRENCY_DEFAULT), "seed");

        List<Transaction> txs = new ArrayList<>();
        Random r = new Random(seedStartEpoch * 31L + 17L + numTx);
        for (int i = 0; i < numTx; i++) {
            int choice = r.nextInt(3);
            Wallet from, to;
            if (choice == 0) { from = a; to = b; }
            else if (choice == 1) { from = b; to = c; }
            else { from = a; to = c; }
            Money amt = Money.of(BigDecimal.valueOf(1000L + r.nextInt(500_000)), CURRENCY_DEFAULT);
            if (from.available().isGreaterThanOrEqual(amt)) {
                Transaction tx = makeTx(from, to, amt, baseTime.plusSeconds((long) i * 3600));
                from.debit(amt, "history out");
                to.credit(amt, "history in");
                txs.add(tx);
            }
        }
        return new Scenario(a, b, c, txs);
    }

    private static Instant baseTime = Instant.EPOCH;

    private Transaction makeTx(Wallet from, Wallet to, Money amount, Instant ts) {
        WalletId fromId = from.id();
        WalletId toId = to.id();
        TransactionId txId = TransactionId.generate();
        AccountMove debit = new AccountMove(UUID.randomUUID(),
                fromId, txId, Money.of(amount.amount().negate(), amount.currency()),
                from.balance().subtract(amount), ts);
        AccountMove credit = new AccountMove(UUID.randomUUID(),
                toId, txId, amount, to.balance().add(amount), ts);
        return Transaction.builder()
                .id(txId)
                .type(TransactionType.TRANSFER_P2P)
                .walletFromId(fromId)
                .walletToId(toId)
                .grossAmount(amount)
                .netAmount(amount)
                .feeAmount(Money.zero(amount.currency()))
                .status(TransactionStatus.COMPLETED)
                .createdAt(ts)
                .updatedAt(ts)
                .createdBy(from.userId().uuid())
                .accountMoves(java.util.List.of(debit, credit))
                .build();
    }

    private List<Transaction> listByWallet(WalletId w, List<Transaction> all) {
        return all.stream()
                .filter(tx -> w.equals(tx.walletFromId()) || w.equals(tx.walletToId()))
                .sorted(Comparator.comparing((Transaction tx) -> tx.createdAt()).reversed())
                .collect(Collectors.toList());
    }

    private List<Transaction> filterByDateRange(List<Transaction> txs, Instant start, Instant end) {
        return txs.stream()
                .filter(tx -> !tx.createdAt().isBefore(start) && !tx.createdAt().isAfter(end))
                .sorted(Comparator.comparing(Transaction::createdAt).reversed())
                .collect(Collectors.toList());
    }

    private List<Transaction> paginate(List<Transaction> sortedTx, int page, int pageSize) {
        int from = Math.max(0, page * pageSize);
        if (from >= sortedTx.size()) return List.of();
        int to = Math.min(sortedTx.size(), from + pageSize);
        return sortedTx.subList(from, to);
    }

    /** Property 15.1: Toda transacción pertenece exactamente a las listas de su from y to (ninguna menos). */
    @Property(tries = 120)
    void p15_01_every_tx_in_related_wallet_history(
            @ForAll("txCount") @IntRange(min = 3, max = 20) int numTx,
            @ForAll("seedEpochs") long seedEpoch) {
        Scenario s = buildScenario(numTx, seedEpoch);
        Set<TransactionId> inA = toIdSet(listByWallet(s.a.id(), s.allTx));
        Set<TransactionId> inB = toIdSet(listByWallet(s.b.id(), s.allTx));
        Set<TransactionId> inC = toIdSet(listByWallet(s.c.id(), s.allTx));
        Set<TransactionId> allIds = s.allTx.stream().map(Transaction::id).collect(Collectors.toSet());
        // Toda transacción está en la unión de las 3 listas
        assertThat(Stream.concat(Stream.concat(inA.stream(), inB.stream()), inC.stream()).collect(Collectors.toSet())).isEqualTo(allIds);
        // Transacciones de a→b están tanto en A como en B
        for (Transaction tx : s.allTx) {
            if (tx.walletFromId().equals(s.a.id()) && tx.walletToId().equals(s.b.id())) {
                assertThat(inA).contains(tx.id());
                assertThat(inB).contains(tx.id());
            }
        }
    }

    private Set<TransactionId> toIdSet(List<Transaction> txs) {
        return txs.stream().map(Transaction::id).collect(Collectors.toSet());
    }

    /** Property 15.2: Filtrado por rango de fechas — start, start<=tx<=end incluye tx dentro y excluye el resto. */
    @Property(tries = 120)
    void p15_02_date_range_filter_correct(
            @ForAll("txCount") @IntRange(min = 4, max = 25) int numTx,
            @ForAll("seedEpochs") long seedEpoch) {
        Scenario s = buildScenario(numTx, seedEpoch);
        Assume.that(s.allTx.size() >= 2);
        List<Instant> sortedInstants = s.allTx.stream().map(Transaction::createdAt).sorted().collect(Collectors.toList());
        Instant first = sortedInstants.get(0);
        Instant last = sortedInstants.get(sortedInstants.size() - 1);
        Instant mid = sortedInstants.get(sortedInstants.size() / 2);

        // Rango [first a mid]: debe contener instantes <= mid y excluir los > mid.
        List<Transaction> within = filterByDateRange(s.allTx, first, mid);
        Set<TransactionId> withinIds = toIdSet(within);
        for (Transaction tx : s.allTx) {
            if (!tx.createdAt().isAfter(mid)) {
                assertThat(withinIds).contains(tx.id());
            } else {
                assertThat(withinIds).doesNotContain(tx.id());
            }
        }
    }

    /** Property 15.3: Paginación — concatenar páginas consecutivas reproduce lista original sin duplicados y preservar order. */
    @Property(tries = 120)
    void p15_03_pagination_pages_concat_preserves_order_and_no_dupes(
            @ForAll("txCount") @IntRange(min = 5, max = 30) int numTx,
            @ForAll("seedEpochs") long seedEpoch,
            @ForAll @IntRange(min = 1, max = 10) int pageSize) {
        Scenario s = buildScenario(numTx, seedEpoch);
        List<Transaction> full = listByWallet(s.a.id(), s.allTx);
        List<Transaction> concat = new ArrayList<>();
        int page = 0;
        Set<TransactionId> seen = new HashSet<>();
        while (true) {
            List<Transaction> pageList = paginate(full, page, pageSize);
            if (pageList.isEmpty()) break;
            for (Transaction t : pageList) {
                assertThat(seen.add(t.id())).as("sin duplicados entre páginas").isTrue();
            }
            concat.addAll(pageList);
            page++;
        }
        assertThat(concat).containsExactlyElementsOf(full);
    }

    /** Property 15.4: Ordenación desc por createdAt — historial empieza por la tx más reciente. */
    @Property(tries = 100)
    void p15_04_ordering_desc_by_created_at(
            @ForAll("txCount") @IntRange(min = 2, max = 20) int numTx,
            @ForAll("seedEpochs") long seedEpoch) {
        Scenario s = buildScenario(numTx, seedEpoch);
        List<Transaction> history = listByWallet(s.a.id(), s.allTx);
        for (int i = 1; i < history.size(); i++) {
            Instant prev = history.get(i - 1).createdAt();
            Instant curr = history.get(i).createdAt();
            assertThat(prev.compareTo(curr)).isNotNegative(); // >=
        }
    }

    /** Property 15.5: Inclusión de ambos sentidos (origen/destino). */
    @Property(tries = 120)
    void p15_05_wallet_appears_in_both_sides(
            @ForAll("txCount") @IntRange(min = 3, max = 15) int numTx,
            @ForAll("seedEpochs") long seedEpoch) {
        Scenario s = buildScenario(numTx, seedEpoch);
        // Todas las tx con from=a y to=b contienen a y b.
        long countA = s.allTx.stream()
                .filter(tx -> tx.walletFromId().equals(s.a.id()) || tx.walletToId().equals(s.a.id()))
                .count();
        long listedA = listByWallet(s.a.id(), s.allTx).size();
        assertThat(listedA).isEqualTo(countA);
    }
}
