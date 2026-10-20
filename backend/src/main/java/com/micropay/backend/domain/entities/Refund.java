package com.micropay.backend.domain.entities;

import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.RefundId;
import com.micropay.backend.domain.valueobjects.RefundType;
import com.micropay.backend.domain.valueobjects.TransactionId;
import com.micropay.backend.domain.valueobjects.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Refund — Reembolso (total o parcial) de una transacción completada (Requirement 23).
 * <p>
 * INVARIANTES (Business Rules):
 *   BR#01: refundedAmount > 0.
 *   BR#02: refundTxId obligatorio (transacción de reverse transfer que movió los fondos).
 *   BR#03: type == FULL sólo cuando el acumulado de reembolsos alcanza el monto original.
 */
public final class Refund {

    private final RefundId id;
    private final TransactionId sourceTransactionId;
    private final UserId refundingUserId;
    private final Money refundedAmount;
    private final RefundType type;
    private final String reason;
    private final TransactionId refundTxId;
    private final Instant createdAt;

    private Refund(Builder b) {
        if (b.refundedAmount == null || !b.refundedAmount.isPositive()) {
            throw new BusinessRuleViolationException("REFUND_AMOUNT_INVALID",
                    "Refund requiere monto > 0");
        }
        this.id = b.id == null ? RefundId.generate() : b.id;
        this.sourceTransactionId = Objects.requireNonNull(b.sourceTransactionId, "sourceTransactionId");
        this.refundingUserId = Objects.requireNonNull(b.refundingUserId, "refundingUserId");
        this.refundedAmount = b.refundedAmount;
        this.type = b.type == null ? RefundType.PARTIAL : b.type;
        this.reason = b.reason;
        this.refundTxId = Objects.requireNonNull(b.refundTxId, "refundTxId");
        this.createdAt = b.createdAt == null ? Instant.now() : b.createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public RefundId id() { return id; }
    public TransactionId sourceTransactionId() { return sourceTransactionId; }
    public UserId refundingUserId() { return refundingUserId; }
    public Money refundedAmount() { return refundedAmount; }
    public RefundType type() { return type; }
    public String reason() { return reason; }
    public TransactionId refundTxId() { return refundTxId; }
    public Instant createdAt() { return createdAt; }

    public static final class Builder {
        private RefundId id;
        private TransactionId sourceTransactionId;
        private UserId refundingUserId;
        private Money refundedAmount;
        private RefundType type;
        private String reason;
        private TransactionId refundTxId;
        private Instant createdAt;

        public Builder id(RefundId v) { this.id = v; return this; }
        public Builder sourceTransactionId(TransactionId v) { this.sourceTransactionId = v; return this; }
        public Builder refundingUserId(UserId v) { this.refundingUserId = v; return this; }
        public Builder refundedAmount(Money v) { this.refundedAmount = v; return this; }
        public Builder type(RefundType v) { this.type = v; return this; }
        public Builder reason(String v) { this.reason = v; return this; }
        public Builder refundTxId(TransactionId v) { this.refundTxId = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }

        public Refund build() { return new Refund(this); }
    }
}
