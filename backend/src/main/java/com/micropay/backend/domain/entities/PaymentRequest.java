package com.micropay.backend.domain.entities;

import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.PaymentRequestId;
import com.micropay.backend.domain.valueobjects.PaymentRequestStatus;
import com.micropay.backend.domain.valueobjects.ShortCode;
import com.micropay.backend.domain.valueobjects.TransactionId;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * PaymentRequest — Solicitud de pago de un comercio (link/QR) que un cliente
 * liquida con una transferencia P2P a la wallet del comercio.
 *
 * Flujo de vida:
 *   PENDING ──(pago P2P OK)──> PAID ──(refund)──> REFUNDED
 *      │
 *      ├──(cancel)──> CANCELLED
 *      └──(expira)──> EXPIRED
 *
 * INVARIANTES (Business Rules):
 *   BR#01: amount > 0 y en la misma moneda de la wallet del comercio (validado en service).
 *   BR#02: expiresAt > createdAt.
 *   BR#03: transiciones válidas: PENDING → PAID/EXPIRED/CANCELLED; PAID → REFUNDED;
 *          PAID/EXPIRED/CANCELLED/REFUNDED terminales.
 *   BR#04: markPaid es idempotente por transacción: un segundo intento con la MISMA tx no falla;
 *          con una tx distinta se rechaza (previene doble pago — Property 25).
 *   BR#05: shortCode inmutable, único a nivel persistencia.
 */
public final class PaymentRequest {

    /** Expiración por defecto si el comercio no la especifica (24 h). */
    public static final Duration DEFAULT_EXPIRY = Duration.ofHours(24);

    private final PaymentRequestId id;
    private final UserId merchantUserId;
    private final WalletId merchantWalletId;
    private final Money amount;
    private final String description;
    private final String orderReference;
    private final ShortCode shortCode;
    private final String webhookUrl;
    private final Instant expiresAt;
    private final Instant createdAt;
    private PaymentRequestStatus status;
    private TransactionId paidWithTxId;
    private Instant paidAt;

    private PaymentRequest(Builder b) {
        if (b.amount == null || !b.amount.isPositive()) {
            throw new BusinessRuleViolationException("PAYMENT_REQUEST_AMOUNT_INVALID",
                    "PaymentRequest requiere monto > 0");
        }
        this.id = b.id == null ? PaymentRequestId.generate() : b.id;
        this.merchantUserId = Objects.requireNonNull(b.merchantUserId, "merchantUserId");
        this.merchantWalletId = Objects.requireNonNull(b.merchantWalletId, "merchantWalletId");
        this.amount = b.amount;
        this.description = b.description;
        this.orderReference = b.orderReference;
        this.shortCode = Objects.requireNonNull(b.shortCode, "shortCode");
        this.webhookUrl = b.webhookUrl;
        this.createdAt = b.createdAt == null ? Instant.now() : b.createdAt;
        this.expiresAt = b.expiresAt == null ? this.createdAt.plus(DEFAULT_EXPIRY) : b.expiresAt;
        if (!this.expiresAt.isAfter(this.createdAt)) {
            throw new BusinessRuleViolationException("PAYMENT_REQUEST_EXPIRY_INVALID",
                    "expiresAt debe ser posterior a createdAt");
        }
        this.status = b.status == null ? PaymentRequestStatus.PENDING : b.status;
        this.paidWithTxId = b.paidWithTxId;
        this.paidAt = b.paidAt;
    }

    public static Builder builder() { return new Builder(); }

    // ────────── Comportamiento (BR#03/#04) ──────────

    /**
     * Marca PAID vinculando la transacción que la liquidó.
     * Idempotente si ya PAID con la MISMA tx; rechaza si ya PAID con tx distinta (anti doble pago).
     */
    public void markPaid(TransactionId txId) {
        Objects.requireNonNull(txId, "txId");
        if (this.status == PaymentRequestStatus.PAID) {
            if (!txId.equals(this.paidWithTxId)) {
                throw new BusinessRuleViolationException("PAYMENT_REQUEST_ALREADY_PAID",
                        "PaymentRequest %s ya fue pagada con tx=%s (intento tx=%s)"
                                .formatted(id, paidWithTxId, txId));
            }
            return; // idempotente
        }
        if (this.status != PaymentRequestStatus.PENDING) {
            throw new BusinessRuleViolationException("PAYMENT_REQUEST_STATUS_TRANSITION_INVALID",
                    "No se puede marcar PAID desde estado " + this.status);
        }
        this.status = PaymentRequestStatus.PAID;
        this.paidWithTxId = txId;
        this.paidAt = Instant.now();
    }

    /** Marca EXPIRED (terminal). Idempotente si ya EXPIRED. */
    public void markExpired() {
        if (this.status == PaymentRequestStatus.EXPIRED) return;
        if (this.status != PaymentRequestStatus.PENDING) {
            throw new BusinessRuleViolationException("PAYMENT_REQUEST_STATUS_TRANSITION_INVALID",
                    "No se puede marcar EXPIRED desde estado " + this.status);
        }
        this.status = PaymentRequestStatus.EXPIRED;
    }

    /** Cancela un request aún no pagado (terminal). Idempotente si ya CANCELLED. */
    public void markCancelled() {
        if (this.status == PaymentRequestStatus.CANCELLED) return;
        if (this.status != PaymentRequestStatus.PENDING) {
            throw new BusinessRuleViolationException("PAYMENT_REQUEST_STATUS_TRANSITION_INVALID",
                    "Solo PENDING puede cancelarse, estado actual " + this.status);
        }
        this.status = PaymentRequestStatus.CANCELLED;
    }

    /** Marca REFUNDED (terminal). Sólo desde PAID. Idempotente si ya REFUNDED. */
    public void markRefunded() {
        if (this.status == PaymentRequestStatus.REFUNDED) return;
        if (this.status != PaymentRequestStatus.PAID) {
            throw new BusinessRuleViolationException("PAYMENT_REQUEST_STATUS_TRANSITION_INVALID",
                    "Solo PAID puede pasar a REFUNDED, estado actual " + this.status);
        }
        this.status = PaymentRequestStatus.REFUNDED;
    }

    public boolean isPending() { return status == PaymentRequestStatus.PENDING; }

    public boolean isTerminal() {
        return status == PaymentRequestStatus.PAID
                || status == PaymentRequestStatus.EXPIRED
                || status == PaymentRequestStatus.CANCELLED
                || status == PaymentRequestStatus.REFUNDED;
    }

    /** true si la fecha límite ya se cumplió respecto a {@code now}. */
    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    // ────────── Getters ──────────

    public PaymentRequestId id() { return id; }
    public UserId merchantUserId() { return merchantUserId; }
    public WalletId merchantWalletId() { return merchantWalletId; }
    public Money amount() { return amount; }
    public String description() { return description; }
    public String orderReference() { return orderReference; }
    public ShortCode shortCode() { return shortCode; }
    public String webhookUrl() { return webhookUrl; }
    public Instant expiresAt() { return expiresAt; }
    public Instant createdAt() { return createdAt; }
    public PaymentRequestStatus status() { return status; }
    public TransactionId paidWithTxId() { return paidWithTxId; }
    public Instant paidAt() { return paidAt; }

    // ────────── Builder ──────────

    public static final class Builder {
        private PaymentRequestId id;
        private UserId merchantUserId;
        private WalletId merchantWalletId;
        private Money amount;
        private String description;
        private String orderReference;
        private ShortCode shortCode;
        private String webhookUrl;
        private Instant expiresAt;
        private Instant createdAt;
        private PaymentRequestStatus status;
        private TransactionId paidWithTxId;
        private Instant paidAt;

        public Builder id(PaymentRequestId v) { this.id = v; return this; }
        public Builder merchantUserId(UserId v) { this.merchantUserId = v; return this; }
        public Builder merchantWalletId(WalletId v) { this.merchantWalletId = v; return this; }
        public Builder amount(Money v) { this.amount = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder orderReference(String v) { this.orderReference = v; return this; }
        public Builder shortCode(ShortCode v) { this.shortCode = v; return this; }
        public Builder webhookUrl(String v) { this.webhookUrl = v; return this; }
        public Builder expiresAt(Instant v) { this.expiresAt = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder status(PaymentRequestStatus v) { this.status = v; return this; }
        public Builder paidWithTxId(TransactionId v) { this.paidWithTxId = v; return this; }
        public Builder paidAt(Instant v) { this.paidAt = v; return this; }

        public PaymentRequest build() { return new PaymentRequest(this); }
    }
}
