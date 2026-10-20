package com.micropay.backend.application.services;

import com.micropay.backend.domain.entities.AccountMove;
import com.micropay.backend.domain.entities.Transaction;
import com.micropay.backend.domain.entities.Wallet;
import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.exceptions.WalletNotFoundException;
import com.micropay.backend.domain.ports.in.FxUseCases;
import com.micropay.backend.domain.ports.out.FxRatePort;
import com.micropay.backend.domain.ports.out.TransactionRepository;
import com.micropay.backend.domain.ports.out.WalletLockAdapter;
import com.micropay.backend.domain.ports.out.WalletRepository;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.FxQuote;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.TransactionStatus;
import com.micropay.backend.domain.valueobjects.TransactionType;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Casos de uso multi-moneda / FX (T26 / Requisitos 29.2, 29.3, 29.6).
 * <p>
 * Feature-flagged: toda la operativa de conversión queda detrás de
 * {@code micropay.multicurrency.enabled} (OFF por defecto). Con el flag OFF el sistema
 * se comporta exactamente igual que antes (el método {@link #convert} rechaza la operación).
 * <p>
 * Conversión atómica: locks distribuidos ordenados por UUID (patrón {@code TransactionService}),
 * débito en el wallet origen + crédito en el wallet destino del mismo usuario, y una
 * {@code Transaction FX_CONVERSION} con 2 {@link AccountMove} y metadata con el rate aplicado.
 */
@Service
public class FxService implements FxUseCases {

    private static final Logger log = LoggerFactory.getLogger(FxService.class);

    private final FxRatePort fx;
    private final WalletRepository wallets;
    private final TransactionRepository transactions;
    private final WalletLockAdapter walletLock;
    private final TransactionTemplate conversionTransaction;
    private final boolean multiCurrencyEnabled;

    public FxService(FxRatePort fx,
                     WalletRepository wallets,
                     TransactionRepository transactions,
                     WalletLockAdapter walletLock,
                     PlatformTransactionManager transactionManager,
                     @Value("${micropay.multicurrency.enabled:false}") boolean multiCurrencyEnabled) {
        this.fx = fx;
        this.wallets = wallets;
        this.transactions = transactions;
        this.walletLock = walletLock;
        this.multiCurrencyEnabled = multiCurrencyEnabled;
        this.conversionTransaction = new TransactionTemplate(transactionManager);
        this.conversionTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional(readOnly = true)
    public FxQuote getQuote(Currency base, Currency quote) {
        FxQuote q = fx.quote(base, quote);
        log.atInfo()
                .addKeyValue("operation", "fxQuote")
                .addKeyValue("base", base.isoCode())
                .addKeyValue("quote", quote.isoCode())
                .addKeyValue("rate", q.rate().toPlainString())
                .addKeyValue("source", q.source())
                .log("FX quote {}->{} rate={} source={}", base.isoCode(), quote.isoCode(), q.rate(), q.source());
        return q;
    }

    @Override
    public Transaction convert(WalletId fromWalletId, Currency targetCurrency, Money amount, UserId initiatedBy) {
        if (!multiCurrencyEnabled) {
            throw new BusinessRuleViolationException("MULTICURRENCY_DISABLED",
                    "La conversión multi-moneda está deshabilitada (micropay.multicurrency.enabled=false)");
        }
        if (fromWalletId == null) throw new IllegalArgumentException("fromWalletId es requerido");
        if (targetCurrency == null) throw new IllegalArgumentException("targetCurrency es requerido");
        if (amount == null || !amount.isPositive()) {
            throw new BusinessRuleViolationException("AMOUNT_NOT_POSITIVE", "El monto de conversión debe ser > 0");
        }
        Wallet target = wallets.findByUserAndCurrency(initiatedBy, targetCurrency)
                .orElseThrow(() -> new BusinessRuleViolationException("FX_TARGET_WALLET_NOT_FOUND",
                        "El usuario %s no tiene wallet en %s".formatted(initiatedBy, targetCurrency)));
        WalletId targetWalletId = target.id();
        if (fromWalletId.equals(targetWalletId)) {
            throw new BusinessRuleViolationException("SAME_WALLET_FX",
                    "No se puede convertir a la misma wallet");
        }

        // Locks distribuidos ordenados por UUID (previene deadlock A↔B).
        WalletId first, second;
        if (fromWalletId.uuid().compareTo(targetWalletId.uuid()) < 0) {
            first = fromWalletId; second = targetWalletId;
        } else {
            first = targetWalletId; second = fromWalletId;
        }
        acquireLockOrFail(first, "fxConvert");
        boolean secondAcquired = false;
        try {
            acquireLockOrFail(second, "fxConvert");
            secondAcquired = true;
            // El commit ocurre DENTRO del TransactionTemplate, antes de soltar los locks.
            return conversionTransaction.execute(status ->
                    doConvert(fromWalletId, targetWalletId, targetCurrency, amount, initiatedBy));
        } finally {
            if (secondAcquired) walletLock.unlock(second);
            walletLock.unlock(first);
        }
    }

    private Transaction doConvert(WalletId fromWalletId,
                                  WalletId targetWalletId,
                                  Currency targetCurrency,
                                  Money amount,
                                  UserId initiatedBy) {
        Wallet from = wallets.findById(fromWalletId)
                .orElseThrow(() -> new WalletNotFoundException(fromWalletId.uuid()));
        Wallet to = wallets.findById(targetWalletId)
                .orElseThrow(() -> new WalletNotFoundException(targetWalletId.uuid()));

        if (!from.userId().equals(initiatedBy) || !to.userId().equals(initiatedBy)) {
            throw new BusinessRuleViolationException("WALLET_NOT_OWNED_BY_USER",
                    "Ambos wallets deben pertenecer al usuario %s".formatted(initiatedBy));
        }
        if (!from.currency().equals(amount.currency())) {
            throw new BusinessRuleViolationException("CURRENCY_MISMATCH",
                    "El monto está en %s pero el wallet origen es %s"
                            .formatted(amount.currency(), from.currency()));
        }
        if (!to.currency().equals(targetCurrency)) {
            throw new BusinessRuleViolationException("CURRENCY_MISMATCH",
                    "El wallet destino es %s pero se esperaba %s".formatted(to.currency(), targetCurrency));
        }

        FxQuote quote = fx.quote(from.currency(), targetCurrency);

        Money balanceAfterDebit = from.debit(amount, "fx.convert");
        Money converted = quote.convert(amount);
        Money balanceAfterCredit = to.credit(converted, "fx.convert");

        wallets.save(from);
        wallets.save(to);

        Money zeroFee = Money.zero(amount.currency());
        Transaction.Builder txBuilder = Transaction.builder()
                .type(TransactionType.FX_CONVERSION)
                .walletFromId(fromWalletId)
                .walletToId(targetWalletId)
                .grossAmount(amount)
                .netAmount(amount)
                .feeAmount(zeroFee)
                .status(TransactionStatus.COMPLETED)
                .createdBy(initiatedBy.uuid())
                .note("FX %s->%s rate=%s".formatted(from.currency().isoCode(), targetCurrency.isoCode(), quote.rate().toPlainString()))
                .metadata(Map.of(
                        "fxBase", from.currency().isoCode(),
                        "fxQuote", targetCurrency.isoCode(),
                        "fxRate", quote.rate().toPlainString(),
                        "fxSource", quote.source()));

        Transaction tx = txBuilder.build();
        List<AccountMove> moves = new ArrayList<>(2);
        moves.add(new AccountMove(UUID.randomUUID(),
                fromWalletId, tx.id(),
                Money.of(amount.amount().negate(), from.currency()),
                balanceAfterDebit, Instant.now()));
        moves.add(new AccountMove(UUID.randomUUID(),
                targetWalletId, tx.id(),
                converted, balanceAfterCredit, Instant.now()));
        txBuilder.accountMoves(moves);
        tx = txBuilder.build();

        Transaction saved = transactions.save(tx);
        log.atInfo()
                .addKeyValue("operation", "fxConvert")
                .addKeyValue("tx_id", String.valueOf(saved.id()))
                .addKeyValue("from_wallet", String.valueOf(fromWalletId))
                .addKeyValue("to_wallet", String.valueOf(targetWalletId))
                .addKeyValue("debited", amount.amount().toPlainString() + " " + from.currency())
                .addKeyValue("credited", converted.amount().toPlainString() + " " + targetCurrency)
                .log("FX CONVERSION COMPLETED tx={} {}->{}", saved.id(), from.currency(), targetCurrency);
        return saved;
    }

    private void acquireLockOrFail(WalletId id, String op) {
        if (!walletLock.tryLock(id)) {
            log.warn("FX_{}_WALLET_LOCK_CONFLICT wallet={}", op, id);
            throw new BusinessRuleViolationException("WALLET_LOCK_CONFLICT",
                    "%s: no pudo adquirir lock distribuido para wallet %s. Reintente en %ds."
                            .formatted(op, id, WalletLockAdapter.TTL_SECONDS));
        }
    }
}
