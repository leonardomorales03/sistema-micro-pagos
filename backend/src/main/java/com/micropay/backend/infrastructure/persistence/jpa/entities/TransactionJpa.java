package com.micropay.backend.infrastructure.persistence.jpa.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class TransactionJpa {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 32, nullable = false)
    private TxType type;

    @Column(name = "short_code", length = 8, unique = true)
    private String shortCode;

    @Column(name = "wallet_from_id", columnDefinition = "uuid")
    private UUID walletFromId;

    @Column(name = "wallet_to_id", columnDefinition = "uuid")
    private UUID walletToId;

    @Column(name = "gross_amount", precision = 24, scale = 8, nullable = false)
    private BigDecimal grossAmount;

    @Column(name = "net_amount", precision = 24, scale = 8, nullable = false)
    private BigDecimal netAmount;

    @Column(name = "fee_amount", precision = 24, scale = 8, nullable = false)
    private BigDecimal feeAmount;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private TxStatus status;

    @Column(name = "external_ref", length = 64, unique = true)
    private String externalRef;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    @Column(name = "created_by", columnDefinition = "uuid")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum TxType {
        TRANSFER_P2P, DEPOSIT_TOPUP, WITHDRAW, PAYMENT_REQUEST,
        SUBSCRIPTION_CHARGE, REFUND, REFERRAL_REWARD, FEE_ADJUSTMENT, FX_CONVERSION
    }

    public enum TxStatus {
        PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED, REFUNDED, EXPIRED, ON_HOLD
    }

    public TransactionJpa() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public TxType getType() { return type; }
    public void setType(TxType type) { this.type = type; }

    public String getShortCode() { return shortCode; }
    public void setShortCode(String shortCode) { this.shortCode = shortCode; }

    public UUID getWalletFromId() { return walletFromId; }
    public void setWalletFromId(UUID walletFromId) { this.walletFromId = walletFromId; }

    public UUID getWalletToId() { return walletToId; }
    public void setWalletToId(UUID walletToId) { this.walletToId = walletToId; }

    public BigDecimal getGrossAmount() { return grossAmount; }
    public void setGrossAmount(BigDecimal grossAmount) { this.grossAmount = grossAmount; }

    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }

    public BigDecimal getFeeAmount() { return feeAmount; }
    public void setFeeAmount(BigDecimal feeAmount) { this.feeAmount = feeAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public TxStatus getStatus() { return status; }
    public void setStatus(TxStatus status) { this.status = status; }

    public String getExternalRef() { return externalRef; }
    public void setExternalRef(String externalRef) { this.externalRef = externalRef; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
