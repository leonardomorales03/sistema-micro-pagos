package com.micropay.backend.infrastructure.persistence.jpa.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_requests")
public class PaymentRequestJpa {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "wallet_to_id", columnDefinition = "uuid", nullable = false)
    private UUID walletToId;

    @Column(name = "short_code", length = 8, nullable = false, unique = true)
    private String shortCode;

    @Column(name = "amount", precision = 24, scale = 8, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private PayReqStatus status;

    @Column(name = "paid_transaction_id", columnDefinition = "uuid")
    private UUID paidTransactionId;

    @Column(name = "created_by", columnDefinition = "uuid", nullable = false)
    private UUID createdBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Lob
    @Column(name = "payload_metadata", columnDefinition = "jsonb")
    private String payloadMetadata;

    public enum PayReqStatus { PENDING, PAID, CANCELLED, EXPIRED }

    public PaymentRequestJpa() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getWalletToId() { return walletToId; }
    public void setWalletToId(UUID walletToId) { this.walletToId = walletToId; }

    public String getShortCode() { return shortCode; }
    public void setShortCode(String shortCode) { this.shortCode = shortCode; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public PayReqStatus getStatus() { return status; }
    public void setStatus(PayReqStatus status) { this.status = status; }

    public UUID getPaidTransactionId() { return paidTransactionId; }
    public void setPaidTransactionId(UUID paidTransactionId) { this.paidTransactionId = paidTransactionId; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getPayloadMetadata() { return payloadMetadata; }
    public void setPayloadMetadata(String payloadMetadata) { this.payloadMetadata = payloadMetadata; }
}
