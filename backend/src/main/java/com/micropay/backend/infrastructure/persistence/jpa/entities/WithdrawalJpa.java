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
@Table(name = "withdrawals", indexes = {
        @Index(name = "withdrawals_user_id_idx", columnList = "user_id"),
        @Index(name = "withdrawals_wallet_id_idx", columnList = "wallet_id"),
        @Index(name = "withdrawals_status_idx", columnList = "status")
})
public class WithdrawalJpa {

    @Id
    @Column(name = "id", columnDefinition = "uuid", nullable = false)
    private UUID id;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    @Column(name = "wallet_id", columnDefinition = "uuid", nullable = false)
    private UUID walletId;

    @Column(name = "payout_destination_id", columnDefinition = "uuid", nullable = false)
    private UUID payoutDestinationId;

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

    @Column(name = "provider_ref", length = 128, unique = true)
    private String providerRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private WithdrawalStatus status;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;

    @Column(name = "created_by", columnDefinition = "uuid")
    private UUID createdBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum WithdrawalStatus {
        REQUESTED, PROCESSING, COMPLETED, FAILED, CANCELLED
    }

    public WithdrawalJpa() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getWalletId() { return walletId; }
    public void setWalletId(UUID walletId) { this.walletId = walletId; }

    public UUID getPayoutDestinationId() { return payoutDestinationId; }
    public void setPayoutDestinationId(UUID payoutDestinationId) { this.payoutDestinationId = payoutDestinationId; }

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

    public WithdrawalStatus getStatus() { return status; }
    public void setStatus(WithdrawalStatus status) { this.status = status; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
