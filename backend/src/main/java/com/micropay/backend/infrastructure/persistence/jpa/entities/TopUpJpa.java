package com.micropay.backend.infrastructure.persistence.jpa.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "topups", indexes = {
        @Index(name = "topups_user_id_idx", columnList = "user_id"),
        @Index(name = "topups_wallet_id_idx", columnList = "wallet_id"),
        @Index(name = "topups_status_idx", columnList = "status"),
        @Index(name = "topups_created_at_idx", columnList = "created_at DESC")
})
public class TopUpJpa {

    @Id
    @Column(name = "id", columnDefinition = "uuid", nullable = false)
    private UUID id;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    @Column(name = "wallet_id", columnDefinition = "uuid", nullable = false)
    private UUID walletId;

    @Column(name = "gross_amount", precision = 24, scale = 8, nullable = false)
    private BigDecimal grossAmount;

    @Column(name = "net_amount", precision = 24, scale = 8, nullable = false)
    private BigDecimal netAmount;

    @Column(name = "fee_amount", precision = 24, scale = 8, nullable = false)
    private BigDecimal feeAmount;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "provider", length = 32, nullable = false)
    private String provider;

    @Column(name = "provider_ref", length = 128, unique = true, nullable = false)
    private String providerRef;

    @Column(name = "checkout_url", length = 1024)
    private String checkoutUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private TopUpStatus status;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "transaction_ref", columnDefinition = "uuid")
    private UUID transactionRef;

    @Column(name = "success_url", length = 1024)
    private String successUrl;

    @Column(name = "cancel_url", length = 1024)
    private String cancelUrl;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;

    @Column(name = "created_by", columnDefinition = "uuid")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum TopUpStatus {
        PENDING_PAYMENT, PAID, FAILED, EXPIRED, CANCELLED, REFUNDED
    }

    public TopUpJpa() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getWalletId() { return walletId; }
    public void setWalletId(UUID walletId) { this.walletId = walletId; }

    public BigDecimal getGrossAmount() { return grossAmount; }
    public void setGrossAmount(BigDecimal grossAmount) { this.grossAmount = grossAmount; }

    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }

    public BigDecimal getFeeAmount() { return feeAmount; }
    public void setFeeAmount(BigDecimal feeAmount) { this.feeAmount = feeAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getProviderRef() { return providerRef; }
    public void setProviderRef(String providerRef) { this.providerRef = providerRef; }

    public String getCheckoutUrl() { return checkoutUrl; }
    public void setCheckoutUrl(String checkoutUrl) { this.checkoutUrl = checkoutUrl; }

    public TopUpStatus getStatus() { return status; }
    public void setStatus(TopUpStatus status) { this.status = status; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }

    public UUID getTransactionRef() { return transactionRef; }
    public void setTransactionRef(UUID transactionRef) { this.transactionRef = transactionRef; }

    public String getSuccessUrl() { return successUrl; }
    public void setSuccessUrl(String successUrl) { this.successUrl = successUrl; }

    public String getCancelUrl() { return cancelUrl; }
    public void setCancelUrl(String cancelUrl) { this.cancelUrl = cancelUrl; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
