package com.micropay.backend.domain.entities;

import com.micropay.backend.domain.valueobjects.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Transaction — Aggregate Root del sistema de pagos.
 * <p>
 * Responsabilidad única: ORQUESTAR movimientos de cuenta doble entrada
 * (2 AccountMove por transacción: DÉBITO wallet_origen + CRÉDITO wallet_destino).
 * <p>
 * INVARIABLES (Reglas negocio - Business Rule):
 *  BR#01 — amount > 0 siempre (monto cero/negativo no permitido)
 *  BR#02 — walletFrom != walletTo (no autotransferencia permitida sin FX explícito)
 *  BR#03 — currencies coinciden: walletFrom.currency == walletTo.currency == amount.currency
 *  BR#04 — walletFrom tiene saldo suficiente disponible (disponible - reserved)
 *  BR#05 — cada AccountMove registra balanceAfter (= snapshot del estado de wallet post-move)
 *  BR#06 — 2 AccountMove por Transaction (P2P, Deposit, Withdraw varían signo)
 *  BR#07 — Status: una vez COMPLETED no puede transitar a otro estado (idempotencia).
 */
public final class Transaction {

    private final TransactionId id;
    private final TransactionType type;
    private final ShortCode shortCode;
    private final WalletId walletFromId;
    private final WalletId walletToId;
    private final Money grossAmount;
    private final Money netAmount;
    private final Money feeAmount;
    private TransactionStatus status;
    private final String externalRef;
    private final Instant createdAt;
    private Instant updatedAt;
    private final UUID createdBy;
    private final List<AccountMove> accountMoves;
    private String failureReason;
    private final String note;

    private Transaction(Builder b) {
        this.id = b.id;
        this.type = b.type;
        this.shortCode = b.shortCode;
        this.walletFromId = b.walletFromId;
        this.walletToId = b.walletToId;
        this.grossAmount = b.grossAmount;
        this.netAmount = b.netAmount;
        this.feeAmount = b.feeAmount;
        this.status = b.status;
        this.externalRef = b.externalRef;
        this.createdAt = b.createdAt;
        this.updatedAt = b.updatedAt;
        this.createdBy = b.createdBy;
        this.accountMoves = new ArrayList<>(b.accountMoves);
        this.failureReason = b.failureReason;
        this.note = b.note;
    }

    public TransactionId id() {
        return id;
    }

    public TransactionType type() {
        return type;
    }

    public ShortCode shortCode() {
        return shortCode;
    }

    public WalletId walletFromId() {
        return walletFromId;
    }

    public WalletId walletToId() {
        return walletToId;
    }

    public Money grossAmount() {
        return grossAmount;
    }

    public Money netAmount() {
        return netAmount;
    }

    public Money feeAmount() {
        return feeAmount;
    }

    public TransactionStatus status() {
        return status;
    }

    public String externalRef() {
        return externalRef;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public UUID createdBy() {
        return createdBy;
    }

    public List<AccountMove> accountMoves() {
        return Collections.unmodifiableList(accountMoves);
    }

    public String failureReason() {
        return failureReason;
    }

    public String note() {
        return note;
    }

    // ─────────────── BUILDER ───────────────

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private TransactionId id = TransactionId.generate();
        private TransactionType type;
        private ShortCode shortCode = ShortCode.random();
        private WalletId walletFromId;
        private WalletId walletToId;
        private Money grossAmount;
        private Money netAmount;
        private Money feeAmount;
        private TransactionStatus status = TransactionStatus.PENDING;
        private String externalRef;
        private Instant createdAt = Instant.now();
        private Instant updatedAt = Instant.now();
        private UUID createdBy;
        private final List<AccountMove> accountMoves = new ArrayList<>();
        private String failureReason;
        private String note;

        public Builder id(TransactionId id) { this.id = id; return this; }
        public Builder type(TransactionType type) { this.type = type; return this; }
        public Builder shortCode(ShortCode s) { this.shortCode = s; return this; }
        public Builder walletFromId(WalletId id) { this.walletFromId = id; return this; }
        public Builder walletToId(WalletId id) { this.walletToId = id; return this; }
        public Builder grossAmount(Money m) { this.grossAmount = m; return this; }
        public Builder netAmount(Money m) { this.netAmount = m; return this; }
        public Builder feeAmount(Money m) { this.feeAmount = m; return this; }
        public Builder status(TransactionStatus s) { this.status = s; return this; }
        public Builder externalRef(String r) { this.externalRef = r; return this; }
        public Builder createdAt(Instant i) { this.createdAt = i; return this; }
        public Builder updatedAt(Instant i) { this.updatedAt = i; return this; }
        public Builder createdBy(UUID u) { this.createdBy = u; return this; }
        public Builder accountMoves(List<AccountMove> m) { this.accountMoves.addAll(m); return this; }
        public Builder failureReason(String r) { this.failureReason = r; return this; }
        public Builder note(String n) { this.note = n; return this; }

        public Transaction build() {
            return new Transaction(this);
        }
    }
}
