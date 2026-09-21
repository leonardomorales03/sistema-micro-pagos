package com.micropay.backend.domain.entities;

import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.TransactionId;
import com.micropay.backend.domain.valueobjects.WalletId;

import java.time.Instant;
import java.util.UUID;

/**
 * AccountMove — LINEA del LIBRO MAYOR (Ledger Double Entry).
 * Cada Transaction genera 2 AccountMove:
 *  - DÉBITO (-)  en walletFromId
 *  - CRÉDITO (+) en walletToId
 *  (Para DEPOSIT: walletToId a crédito; FROM id = null)
 *  (Para WITHDRAW: walletFromId a débito;  TO id = null)
 * <p>
 * INVARIABLE: `balanceAfter` es snapshot del wallet.después del movimiento
 * (permite auditoría sin recalcular histórico).
 */
public final class AccountMove {

    private final UUID id;
    private final WalletId walletId;
    private final TransactionId transactionId;
    private final Money amount; // negativo=DEBITO, positivo=CREDITO
    private final Money balanceAfter;
    private final Instant createdAt;

    public AccountMove(UUID id,
                       WalletId walletId,
                       TransactionId transactionId,
                       Money amount,
                       Money balanceAfter,
                       Instant createdAt) {
        if (id == null) id = UUID.randomUUID();
        this.id = id;
        this.walletId = walletId;
        this.transactionId = transactionId;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public UUID id() { return id; }
    public WalletId walletId() { return walletId; }
    public TransactionId transactionId() { return transactionId; }
    public Money amount() { return amount; }
    public Money balanceAfter() { return balanceAfter; }
    public Instant createdAt() { return createdAt; }

    public boolean isDebit()  { return amount.isNegative(); }
    public boolean isCredit() { return amount.isPositive(); }
}
