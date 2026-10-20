package com.micropay.backend.domain.entities;

import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.valueobjects.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Withdrawal — Retiro de fondos de wallet hacia una cuenta externa
 * (PayoutDestination) vía proveedor de payout.
 *
 * Flujo de vida:
 *   REQUESTED ──(payout provider initiate)──> PROCESSING ──(webhook/polling)──> COMPLETED
 *       │                                          │
 *       └──────────── FAILED / CANCELLED ───────────┘  (reversa: libera reserva en wallet)
 *
 * INVARIANTES (Business Rules):
 *   BR#01: grossAmount > 0; feeAmount >= 0; netAmount = gross - fee > 0
 *   BR#02: currency coincide con wallet.currency (validado en service)
 *   BR#03: status transiciones válidas: REQUESTED → PROCESSING/FAILED/CANCELLED;
 *           PROCESSING → COMPLETED/FAILED; COMPLETED terminal.
 *   BR#04: un Withdrawal en PROCESSING reserva fondos en la wallet (reserved).
 *          Al pasar a FAILED se debe liberar la reserva (releaseReserve) — orquestado en service.
 *   BR#05: providerRef asignado al iniciar el payout; no puede cambiar una vez asignado.
 */
public final class Withdrawal {

    public enum Status {
        REQUESTED, PROCESSING, COMPLETED, FAILED, CANCELLED
    }

    private final WithdrawalId id;
    private final UserId userId;
    private final WalletId walletId;
    private final PayoutDestinationId payoutDestinationId;
    private final Money grossAmount;
    private final Money netAmount;
    private final Money feeAmount;
    private final String provider;
    private String providerRef;
    private Status status;
    private String failureReason;
    private final String note;
    private final Map<String,String> metadata;
    private final UUID createdBy;
    private final Instant requestedAt;
    private Instant processedAt;
    private Instant updatedAt;

    private Withdrawal(Builder b) {
        if (b.grossAmount == null || !b.grossAmount.isPositive()) {
            throw new BusinessRuleViolationException("WITHDRAWAL_GROSS_AMOUNT_NOT_POSITIVE",
                    "Withdrawal requiere monto > 0");
        }
        if (b.netAmount == null || !b.netAmount.isPositive()) {
            throw new BusinessRuleViolationException("WITHDRAWAL_NET_AMOUNT_INVALID",
                    "Withdrawal netAmount debe ser > 0");
        }
        if (b.feeAmount == null || b.feeAmount.isNegative()) {
            throw new BusinessRuleViolationException("WITHDRAWAL_FEE_AMOUNT_INVALID",
                    "Withdrawal feeAmount >= 0");
        }
        this.id = b.id == null ? WithdrawalId.generate() : b.id;
        this.userId = Objects.requireNonNull(b.userId, "userId");
        this.walletId = Objects.requireNonNull(b.walletId, "walletId");
        this.payoutDestinationId = Objects.requireNonNull(b.payoutDestinationId, "payoutDestinationId");
        this.grossAmount = b.grossAmount;
        this.netAmount = b.netAmount;
        this.feeAmount = b.feeAmount;
        this.provider = b.provider == null ? "STRIPE_MOCK" : b.provider;
        this.providerRef = b.providerRef;
        this.status = b.status == null ? Status.REQUESTED : b.status;
        this.failureReason = b.failureReason;
        this.note = b.note;
        this.metadata = b.metadata == null ? new HashMap<>() : new HashMap<>(b.metadata);
        this.createdBy = b.createdBy;
        this.requestedAt = b.requestedAt == null ? Instant.now() : b.requestedAt;
        this.processedAt = b.processedAt;
        this.updatedAt = b.updatedAt == null ? this.requestedAt : b.updatedAt;
    }

    public static Builder builder() { return new Builder(); }

    // ────────── Comportamiento (BR#03/#05) ──────────

    /** Marca PROCESSING y asigna providerRef del payout provider (idempotente si ya mismo ref). */
    public void markProcessing(String providerRef) {
        if (this.status == Status.PROCESSING && Objects.equals(this.providerRef, providerRef)) {
            return; // idempotente
        }
        if (this.status != Status.REQUESTED && this.status != Status.PROCESSING) {
            throw new BusinessRuleViolationException("WITHDRAWAL_STATUS_TRANSITION_INVALID",
                    "No se puede marcar PROCESSING desde estado " + this.status);
        }
        this.providerRef = providerRef;   // BR#05
        this.status = Status.PROCESSING;
        this.updatedAt = Instant.now();
    }

    /** Marca COMPLETED (terminal). Idempotente si ya COMPLETED. */
    public void markCompleted() {
        if (this.status == Status.COMPLETED) return;
        if (this.status != Status.PROCESSING) {
            throw new BusinessRuleViolationException("WITHDRAWAL_STATUS_TRANSITION_INVALID",
                    "No se puede marcar COMPLETED desde estado " + this.status);
        }
        this.status = Status.COMPLETED;
        this.processedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Marca FAILED (terminal). Idempotente si ya FAILED.
     * El caller (service) es responsable de liberar la reserva de fondos en la wallet.
     */
    public void markFailed(String reason) {
        if (this.status == Status.FAILED) return;
        if (this.status == Status.COMPLETED || this.status == Status.CANCELLED) {
            throw new BusinessRuleViolationException("WITHDRAWAL_STATUS_TRANSITION_INVALID",
                    "Withdrawal estado " + this.status + " no permite marcar FAILED");
        }
        this.status = Status.FAILED;
        this.failureReason = reason == null ? "UNKNOWN" : reason;
        this.processedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /** Cancela un retiro aún no enviado al provider. */
    public void cancel() {
        if (this.status == Status.CANCELLED) return;
        if (this.status != Status.REQUESTED) {
            throw new BusinessRuleViolationException("WITHDRAWAL_STATUS_TRANSITION_INVALID",
                    "Solo REQUESTED puede cancelarse, estado actual " + this.status);
        }
        this.status = Status.CANCELLED;
        this.updatedAt = Instant.now();
    }

    /** true si el retiro ya terminó (no requiere reserva de fondos). */
    public boolean isTerminal() {
        return status == Status.COMPLETED || status == Status.FAILED || status == Status.CANCELLED;
    }

    // ────────── Getters ──────────

    public WithdrawalId id() { return id; }
    public UserId userId() { return userId; }
    public WalletId walletId() { return walletId; }
    public PayoutDestinationId payoutDestinationId() { return payoutDestinationId; }
    public Money grossAmount() { return grossAmount; }
    public Money netAmount() { return netAmount; }
    public Money feeAmount() { return feeAmount; }
    public String provider() { return provider; }
    public String providerRef() { return providerRef; }
    public Status status() { return status; }
    public String failureReason() { return failureReason; }
    public String note() { return note; }
    public Map<String,String> metadata() { return metadata; }
    public UUID createdBy() { return createdBy; }
    public Instant requestedAt() { return requestedAt; }
    public Instant processedAt() { return processedAt; }
    public Instant updatedAt() { return updatedAt; }

    public static final class Builder {
        private WithdrawalId id;
        private UserId userId;
        private WalletId walletId;
        private PayoutDestinationId payoutDestinationId;
        private Money grossAmount;
        private Money netAmount;
        private Money feeAmount;
        private String provider;
        private String providerRef;
        private Status status;
        private String failureReason;
        private String note;
        private Map<String,String> metadata;
        private UUID createdBy;
        private Instant requestedAt;
        private Instant processedAt;
        private Instant updatedAt;

        public Builder id(WithdrawalId v){ this.id = v; return this; }
        public Builder userId(UserId v){ this.userId = v; return this; }
        public Builder walletId(WalletId v){ this.walletId = v; return this; }
        public Builder payoutDestinationId(PayoutDestinationId v){ this.payoutDestinationId = v; return this; }
        public Builder grossAmount(Money v){ this.grossAmount = v; return this; }
        public Builder netAmount(Money v){ this.netAmount = v; return this; }
        public Builder feeAmount(Money v){ this.feeAmount = v; return this; }
        public Builder provider(String v){ this.provider = v; return this; }
        public Builder providerRef(String v){ this.providerRef = v; return this; }
        public Builder status(Status v){ this.status = v; return this; }
        public Builder failureReason(String v){ this.failureReason = v; return this; }
        public Builder note(String v){ this.note = v; return this; }
        public Builder metadata(Map<String,String> v){ this.metadata = v; return this; }
        public Builder createdBy(UUID v){ this.createdBy = v; return this; }
        public Builder requestedAt(Instant v){ this.requestedAt = v; return this; }
        public Builder processedAt(Instant v){ this.processedAt = v; return this; }
        public Builder updatedAt(Instant v){ this.updatedAt = v; return this; }

        public Withdrawal build() { return new Withdrawal(this); }
    }
}
