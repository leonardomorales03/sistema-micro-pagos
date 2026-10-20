package com.micropay.backend.domain.entities;

import com.micropay.backend.domain.valueobjects.FeeType;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.TransactionId;

import java.time.Instant;
import java.util.UUID;

/**
 * FeeLedgerEntry — Asiento del libro de comisiones (Requisito 30.2).
 * <p>
 * Cada comisión calculada se registra como un asiento separado ligado a la
 * transacción que la originó, junto con la versión de regla aplicada (Requisito 30.4).
 * Permite conciliación financiera y dashboards segmentados por tipo de fee (30.5).
 */
public final class FeeLedgerEntry {

    private final UUID id;
    private final TransactionId transactionId;
    private final Money amount;
    private final FeeType feeType;
    private final String ruleVersion;
    private final String description;
    private final Instant createdAt;

    public FeeLedgerEntry(UUID id,
                          TransactionId transactionId,
                          Money amount,
                          FeeType feeType,
                          String ruleVersion,
                          String description,
                          Instant createdAt) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.transactionId = transactionId;
        this.amount = amount;
        this.feeType = feeType;
        this.ruleVersion = ruleVersion;
        this.description = description;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public UUID id() { return id; }
    public TransactionId transactionId() { return transactionId; }
    public Money amount() { return amount; }
    public FeeType feeType() { return feeType; }
    public String ruleVersion() { return ruleVersion; }
    public String description() { return description; }
    public Instant createdAt() { return createdAt; }
}
