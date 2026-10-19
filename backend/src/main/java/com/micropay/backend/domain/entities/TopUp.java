package com.micropay.backend.domain.entities;

import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.valueobjects.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * TopUp — Intento de recarga de fondos desde proveedor externo
 * (Stripe Sandbox / Stripe Real / Transferencia Bancaria).
 *
 * Flujo vida: PENDING_PAYMENT → (webhook proveedor) → PAID  →  [crear Transaction DEPOSIT_TOPUP + AccountMove CREDITO a wallet]
 *                                         ↳ FAILED/EXPIRED →  sin side-effects, solo notificación.
 *
 * INVARIABLES:
 *   BR#01: grossAmount > 0, currency coincide con wallet.currency (valida service al crear)
 *   BR#02: netAmount == grossAmount - feeAmount; feeAmount >= 0
 *   BR#03: status transición válida PENDING_PAYMENT solo puede ir a PAID/FAILED/EXPIRED/CANCELLED.
 *   BR#04: PAID es terminal: no se puede volver a PENDING_PAYMENT ni a FAILED.
 *   BR#05: idempotencia: marcarPaid() idempotente (si ya PAID + providerRef coincide no error).
 */
public final class TopUp {

    public enum Status {
        PENDING_PAYMENT, PAID, FAILED, EXPIRED, CANCELLED, REFUNDED
    }

    private final TopUpId id;
    private final UserId userId;
    private final WalletId walletId;
    private final Money grossAmount;
    private final Money netAmount;
    private final Money feeAmount;
    private final String provider;
    private final String providerRef;
    private String checkoutUrl;
    private Status status;
    private String failureReason;
    private Instant paidAt;
    private TransactionId transactionRef;
    private String successUrl;
    private String cancelUrl;
    private String idempotencyKey;
    private final Map<String,String> metadata;
    private final UUID createdBy;
    private final Instant createdAt;
    private Instant updatedAt;

    private TopUp(Builder b) {
        if (b.grossAmount == null || !b.grossAmount.isPositive()) {
            throw new BusinessRuleViolationException("TOPUP_GROSS_AMOUNT_NOT_POSITIVE",
                    "TopUp requiere monto > 0");
        }
        if (b.netAmount == null || !b.netAmount.isPositive()) {
            throw new BusinessRuleViolationException("TOPUP_NET_AMOUNT_INVALID",
                    "TopUp netAmount debe ser > 0");
        }
        if (b.feeAmount == null || b.feeAmount.isNegative()) {
            throw new BusinessRuleViolationException("TOPUP_FEE_AMOUNT_INVALID",
                    "TopUp feeAmount >= 0");
        }
        this.id = b.id == null ? TopUpId.generate() : b.id;
        this.userId = Objects.requireNonNull(b.userId, "userId");
        this.walletId = Objects.requireNonNull(b.walletId, "walletId");
        this.grossAmount = b.grossAmount;
        this.netAmount = b.netAmount;
        this.feeAmount = b.feeAmount;
        this.provider = b.provider == null ? "STRIPE_MOCK" : b.provider;
        this.providerRef = Objects.requireNonNull(b.providerRef, "providerRef");
        this.checkoutUrl = b.checkoutUrl;
        this.status = b.status == null ? Status.PENDING_PAYMENT : b.status;
        this.failureReason = b.failureReason;
        this.paidAt = b.paidAt;
        this.transactionRef = b.transactionRef;
        this.successUrl = b.successUrl;
        this.cancelUrl = b.cancelUrl;
        this.idempotencyKey = b.idempotencyKey;
        this.metadata = b.metadata == null ? new HashMap<>() : new HashMap<>(b.metadata);
        this.createdBy = b.createdBy;
        this.createdAt = b.createdAt == null ? Instant.now() : b.createdAt;
        this.updatedAt = b.updatedAt == null ? this.createdAt : b.updatedAt;
    }

    public static Builder builder() { return new Builder(); }

    // ────────── Comportamiento (BR status transitions) ──────────

    /** Marca PAID y asigna TransactionRef DEPOSIT_TOPUP (idempotente si ya PAID misma ref). */
    public void markPaid(TransactionId txRef) {
        if (this.status == Status.PAID) {
            if (txRef != null && this.transactionRef != null && !this.transactionRef.equals(txRef)) {
                throw new BusinessRuleViolationException("TOPUP_ALREADY_PAID_MISMATCH",
                        "TopUp ya está PAID con tx=" + this.transactionRef + " pero nueva=" + txRef);
            }
            return;
        }
        if (this.status != Status.PENDING_PAYMENT) {
            throw new BusinessRuleViolationException("TOPUP_STATUS_TRANSITION_INVALID",
                    "No se puede marcar PAID desde estado " + this.status);
        }
        this.status = Status.PAID;
        this.paidAt = Instant.now();
        this.transactionRef = txRef;
        this.updatedAt = Instant.now();
    }

    /** Marca FAILED con motivo (idempotente si ya FAILED). */
    public void markFailed(String reason) {
        if (this.status == Status.FAILED || this.status == Status.EXPIRED || this.status == Status.CANCELLED) {
            return;
        }
        if (this.status == Status.PAID || this.status == Status.REFUNDED) {
            throw new BusinessRuleViolationException("TOPUP_ALREADY_PAID",
                    "TopUp estado " + this.status + " no permite marcar FAILED");
        }
        this.status = Status.FAILED;
        this.failureReason = reason == null ? "UNKNOWN" : reason;
        this.updatedAt = Instant.now();
    }

    /** Actualiza checkoutUrl solo si estado PENDING_PAYMENT. */
    public void setCheckoutUrl(String url) {
        if (this.status == Status.PENDING_PAYMENT) {
            this.checkoutUrl = url;
            this.updatedAt = Instant.now();
        }
    }

    // ────────── Getters ──────────

    public TopUpId id() { return id; }
    public UserId userId() { return userId; }
    public WalletId walletId() { return walletId; }
    public Money grossAmount() { return grossAmount; }
    public Money netAmount() { return netAmount; }
    public Money feeAmount() { return feeAmount; }
    public String provider() { return provider; }
    public String providerRef() { return providerRef; }
    public String checkoutUrl() { return checkoutUrl; }
    public Status status() { return status; }
    public String failureReason() { return failureReason; }
    public Instant paidAt() { return paidAt; }
    public TransactionId transactionRef() { return transactionRef; }
    public String successUrl() { return successUrl; }
    public String cancelUrl() { return cancelUrl; }
    public String idempotencyKey() { return idempotencyKey; }
    public Map<String,String> metadata() { return metadata; }
    public UUID createdBy() { return createdBy; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public static final class Builder {
        private TopUpId id;
        private UserId userId;
        private WalletId walletId;
        private Money grossAmount;
        private Money netAmount;
        private Money feeAmount;
        private String provider;
        private String providerRef;
        private String checkoutUrl;
        private Status status;
        private String failureReason;
        private Instant paidAt;
        private TransactionId transactionRef;
        private String successUrl;
        private String cancelUrl;
        private String idempotencyKey;
        private Map<String,String> metadata;
        private UUID createdBy;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(TopUpId id){ this.id = id; return this; }
        public Builder userId(UserId u){ this.userId = u; return this; }
        public Builder walletId(WalletId w){ this.walletId = w; return this; }
        public Builder grossAmount(Money m){ this.grossAmount = m; return this; }
        public Builder netAmount(Money m){ this.netAmount = m; return this; }
        public Builder feeAmount(Money m){ this.feeAmount = m; return this; }
        public Builder provider(String p){ this.provider = p; return this; }
        public Builder providerRef(String r){ this.providerRef = r; return this; }
        public Builder checkoutUrl(String s){ this.checkoutUrl = s; return this; }
        public Builder status(Status s){ this.status = s; return this; }
        public Builder failureReason(String r){ this.failureReason = r; return this; }
        public Builder paidAt(Instant i){ this.paidAt = i; return this; }
        public Builder transactionRef(TransactionId t){ this.transactionRef = t; return this; }
        public Builder successUrl(String s){ this.successUrl = s; return this; }
        public Builder cancelUrl(String s){ this.cancelUrl = s; return this; }
        public Builder idempotencyKey(String s){ this.idempotencyKey = s; return this; }
        public Builder metadata(Map<String,String> m){ this.metadata = m; return this; }
        public Builder createdBy(UUID u){ this.createdBy = u; return this; }
        public Builder createdAt(Instant i){ this.createdAt = i; return this; }
        public Builder updatedAt(Instant i){ this.updatedAt = i; return this; }

        public TopUp build() { return new TopUp(this); }
    }
}
