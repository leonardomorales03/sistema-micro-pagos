package com.micropay.backend.infrastructure.persistence.jpa.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refunds")
public class RefundJpa {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "source_tx_id", columnDefinition = "uuid", nullable = false)
    private UUID sourceTxId;

    @Column(name = "refunding_user_id", columnDefinition = "uuid", nullable = false)
    private UUID refundingUserId;

    @Column(name = "refunded_amount", precision = 24, scale = 8, nullable = false)
    private BigDecimal refundedAmount;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 16, nullable = false)
    private RefundTypeJpa type;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "reason")
    private String reason;

    @Column(name = "refund_tx_id", columnDefinition = "uuid", nullable = false, unique = true)
    private UUID refundTxId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public enum RefundTypeJpa { FULL, PARTIAL }

    public RefundJpa() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSourceTxId() { return sourceTxId; }
    public void setSourceTxId(UUID sourceTxId) { this.sourceTxId = sourceTxId; }

    public UUID getRefundingUserId() { return refundingUserId; }
    public void setRefundingUserId(UUID refundingUserId) { this.refundingUserId = refundingUserId; }

    public BigDecimal getRefundedAmount() { return refundedAmount; }
    public void setRefundedAmount(BigDecimal refundedAmount) { this.refundedAmount = refundedAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public RefundTypeJpa getType() { return type; }
    public void setType(RefundTypeJpa type) { this.type = type; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public UUID getRefundTxId() { return refundTxId; }
    public void setRefundTxId(UUID refundTxId) { this.refundTxId = refundTxId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
