package com.micropay.backend.domain.entities;

import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.valueobjects.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Wallet — Aggregate Root cuenta de fondos individual.
 * <p>
 * Una Wallet pertenece a un User y una Currency.
 * <p>
 * 7 REGLAS DE NEGOCIO (Business Rules) — implementadas AQUÍ:
 *   BR#01: Saldo nunca puede ser negativo → `CHECK (balance >= 0)`
 *   BR#02: Toda operación (credit/debit) requiere moneda coincidente
 *   BR#03: amount > 0 (stricto) en credit/debit
 *   BR#04: `debit` requiere `available >= amount` (no permite sobregiro)
 *   BR#05: `debit` no auto-aplica si Wallet.status != ACTIVE
 *   BR#06: version Optimistic Locking @Version (manejado por infra)
 *   BR#07: createdBy/lastModifiedBy audit trail
 */
public final class Wallet {

    public enum Status { ACTIVE, FROZEN, CLOSED }

    private final WalletId id;
    private final UserId userId;
    private final Currency currency;
    private Money balance;
    private Money reserved;  // fondos retenidos (ej: withdraw en hold 48h)
    private Status status;
    private final Instant createdAt;
    private Instant updatedAt;
    private final UUID createdBy;
    private long version;

    private Wallet(Builder b) {
        this.id = b.id;
        this.userId = b.userId;
        this.currency = b.currency;
        this.balance = Money.zero(b.currency);
        this.reserved = Money.zero(b.currency);
        this.status = b.status;
        this.createdAt = b.createdAt;
        this.updatedAt = b.updatedAt;
        this.createdBy = b.createdBy;
        this.version = 0;
    }

    // ────────── FÁBRICA ──────────
    public static Wallet createFor(UserId userId, Currency currency, UUID createdBy) {
        return Wallet.builder()
                .userId(userId)
                .currency(currency)
                .status(Status.ACTIVE)
                .createdBy(createdBy)
                .build();
    }

    // ────────── COMPORTAMIENTO (7 REGLAS NEGOCIO) ──────────

    /** Saldo disponible = balance - reserved (fondos con hold pendiente) */
    public Money available() {
        return balance.subtract(reserved);
    }

    /** Añade fondos (crédito). BR#02 + BR#03 + BR#05 */
    public Money credit(Money amount, String operation) {
        enforceActive(operation);                 // BR#05
        enforcePositiveAmount(amount, operation); // BR#03
        enforceSameCurrency(amount, operation);   // BR#02

        this.balance = this.balance.add(amount);
        touchUpdatedAt();
        return this.balance;
    }

    /** Retira fondos (débito). BR#02 + BR#03 + BR#04 + BR#01 + BR#05 */
    public Money debit(Money amount, String operation) {
        enforceActive(operation);                 // BR#05
        enforcePositiveAmount(amount, operation); // BR#03
        enforceSameCurrency(amount, operation);   // BR#02
        enforceEnoughAvailable(amount, operation);// BR#04

        Money newBalance = this.balance.subtract(amount);
        if (newBalance.isNegative()) {            // BR#01 (doble check)
            throw new BusinessRuleViolationException("WALLET_NEGATIVE_BALANCE",
                    "%s resultaría en saldo negativo (%s < 0)".formatted(operation, newBalance));
        }
        this.balance = newBalance;
        touchUpdatedAt();
        return this.balance;
    }

    /** Retener fondos (hold) — ej: withdraw con 48h. Reduce available sin mover balance. */
    public void reserve(Money amount, String operation) {
        enforceActive(operation);
        enforcePositiveAmount(amount, operation);
        enforceSameCurrency(amount, operation);
        enforceEnoughAvailable(amount, operation);
        this.reserved = this.reserved.add(amount);
        touchUpdatedAt();
    }

    /** Liberar fondos retenidos (cancelar hold). */
    public void releaseReserve(Money amount, String operation) {
        enforceSameCurrency(amount, operation);
        if (amount.isGreaterThan(this.reserved)) {
            throw new BusinessRuleViolationException("WALLET_RESERVE_OVERRELEASE",
                    "%s intenta liberar %s, reserva actual es %s"
                            .formatted(operation, amount, this.reserved));
        }
        this.reserved = this.reserved.subtract(amount);
        touchUpdatedAt();
    }

    /** Confirmar hold: saca los fondos ya reservados y descuenta balance/reserved. */
    public void confirmReserveAsDebit(Money amount, String operation) {
        enforceSameCurrency(amount, operation);
        if (amount.isGreaterThan(this.reserved)) {
            throw new BusinessRuleViolationException("WALLET_RESERVE_INSUFFICIENT",
                    "%s requiere reserva %s, actual %s".formatted(operation, amount, this.reserved));
        }
        this.reserved = this.reserved.subtract(amount);
        Money newBalance = this.balance.subtract(amount);
        if (newBalance.isNegative()) {
            throw new BusinessRuleViolationException("WALLET_NEGATIVE_BALANCE_RESERVED",
                    "%s resultaría en saldo negativo luego de confirmar reserva".formatted(operation));
        }
        this.balance = newBalance;
        touchUpdatedAt();
    }

    public void freeze()  { this.status = Status.FROZEN;  touchUpdatedAt(); }
    public void close()   { this.status = Status.CLOSED;  touchUpdatedAt(); }
    public void activate(){ this.status = Status.ACTIVE;  touchUpdatedAt(); }

    // ────────── VALIDACIONES PRIVADAS (7 reglas negocio) ──────────
    private void enforceActive(String operation) {
        if (this.status != Status.ACTIVE) {
            throw new BusinessRuleViolationException("WALLET_NOT_ACTIVE",
                    "%s no permitido sobre wallet en estado %s".formatted(operation, this.status));
        }
    }

    private void enforcePositiveAmount(Money amount, String operation) {
        if (!amount.isPositive()) {
            throw new BusinessRuleViolationException("AMOUNT_NOT_POSITIVE",
                    "%s requiere monto > 0, recibió %s".formatted(operation, amount));
        }
    }

    private void enforceSameCurrency(Money amount, String operation) {
        if (!amount.currency().equals(this.currency)) {
            throw new BusinessRuleViolationException("CURRENCY_MISMATCH",
                    "%s requiere %s, recibió %s".formatted(operation, this.currency, amount.currency()));
        }
    }

    private void enforceEnoughAvailable(Money amount, String operation) {
        Money available = available();
        if (available.isLessThan(amount)) {
            throw new BusinessRuleViolationException("INSUFFICIENT_AVAILABLE_BALANCE",
                    "%s requiere %s, disponible %s (%s - %s de reserva)"
                            .formatted(operation, amount, available, this.balance, this.reserved));
        }
    }

    private void touchUpdatedAt() {
        this.updatedAt = Instant.now();
    }

    // ────────── GETTERS (read-only projection) ──────────
    public WalletId id() { return id; }
    public UserId userId() { return userId; }
    public Currency currency() { return currency; }
    public Money balance() { return balance; }
    public Money reserved() { return reserved; }
    public Status status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public UUID createdBy() { return createdBy; }
    public long version() { return version; }

    // ────────── BUILDER ──────────
    public static Builder builder() { return new Builder(); }
    public static final class Builder {
        private WalletId id = WalletId.generate();
        private UserId userId;
        private Currency currency;
        private Status status = Status.ACTIVE;
        private Instant createdAt = Instant.now();
        private Instant updatedAt = Instant.now();
        private UUID createdBy;

        public Builder id(WalletId v) { this.id = v; return this; }
        public Builder userId(UserId v) { this.userId = v; return this; }
        public Builder currency(Currency v) { this.currency = v; return this; }
        public Builder status(Status v) { this.status = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v) { this.updatedAt = v; return this; }
        public Builder createdBy(UUID v) { this.createdBy = v; return this; }

        public Wallet build() {
            if (userId == null) throw new IllegalArgumentException("userId es requerido");
            if (currency == null) throw new IllegalArgumentException("currency es requerido");
            return new Wallet(this);
        }
    }
}
