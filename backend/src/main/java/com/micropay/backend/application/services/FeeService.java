package com.micropay.backend.application.services;

import com.micropay.backend.domain.entities.FeeLedgerEntry;
import com.micropay.backend.domain.entities.Transaction;
import com.micropay.backend.domain.ports.out.FeeEnginePort;
import com.micropay.backend.domain.ports.out.FeeLedgerRepository;
import com.micropay.backend.domain.valueobjects.FeeContext;
import com.micropay.backend.domain.valueobjects.FeeLine;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.TransactionId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Aplicación de comisiones (Requisito 30.2).
 * <p>
 * Helper invocado DENTRO de las transacciones sensibles (transferencia P2P, top-up,
 * retiro, pago de Payment Request, reembolso). Calcula las líneas del FeeEngine y
 * persiste cada una como asiento en {@code fee_ledger} ligado a la transacción que la
 * originó, dentro de la MISMA transacción de base de datos (aplicación atómica).
 * <p>
 * Reglas sin comisión (amount == 0) no generan asiento.
 */
@Service
public class FeeService {

    private static final Logger log = LoggerFactory.getLogger(FeeService.class);

    private final FeeEnginePort feeEngine;
    private final FeeLedgerRepository feeLedger;

    public FeeService(FeeEnginePort feeEngine, FeeLedgerRepository feeLedger) {
        this.feeEngine = feeEngine;
        this.feeLedger = feeLedger;
    }

    /** Cotiza comisiones sin persistir (útil para preview/quote). */
    public List<FeeLine> quote(FeeContext context) {
        return feeEngine.calculateFees(context);
    }

    /** Suma de comisiones cotizadas (sin persistir). Útil para fijar fee/net al construir la transacción. */
    public Money quoteTotal(FeeContext context) {
        Money total = Money.zero(context.currency());
        for (FeeLine line : feeEngine.calculateFees(context)) {
            if (line != null && !line.isZero()) {
                total = total.add(line.amount());
            }
        }
        return total;
    }

    /**
     * Calcula y registra las comisiones de una transacción ya construida.
     * Debe ejecutarse dentro de la transacción de la operación (PROPAGATION_REQUIRED une la del caller).
     *
     * @return suma total de comisiones aplicadas (Money en la moneda del contexto).
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Money applyAndRecord(Transaction transaction, FeeContext context) {
        List<FeeLine> lines = feeEngine.calculateFees(context);
        Money total = Money.zero(context.currency());
        for (FeeLine line : lines) {
            if (line == null || line.isZero()) {
                continue;
            }
            feeLedger.save(new FeeLedgerEntry(
                    UUID.randomUUID(),
                    transaction.id(),
                    line.amount(),
                    line.type(),
                    line.ruleVersion(),
                    line.description(),
                    Instant.now()));
            total = total.add(line.amount());
            log.atInfo()
                    .addKeyValue("operation", context.operation())
                    .addKeyValue("tx_id", String.valueOf(transaction.id()))
                    .addKeyValue("fee_type", line.type())
                    .addKeyValue("fee_amount", line.amount().amount().toPlainString())
                    .addKeyValue("rule_version", line.ruleVersion())
                    .log("Fee registrada para tx {}", transaction.id());
        }
        return total;
    }

    public List<FeeLedgerEntry> feesFor(TransactionId transactionId) {
        return feeLedger.listByTransaction(transactionId);
    }
}
