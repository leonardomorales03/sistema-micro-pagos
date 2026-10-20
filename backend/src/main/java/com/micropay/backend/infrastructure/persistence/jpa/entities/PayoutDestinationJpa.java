package com.micropay.backend.infrastructure.persistence.jpa.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payout_destinations", indexes = {
        @Index(name = "payout_destinations_user_id_idx", columnList = "user_id")
})
public class PayoutDestinationJpa {

    @Id
    @Column(name = "id", columnDefinition = "uuid", nullable = false)
    private UUID id;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 24, nullable = false)
    private DestType type;

    @Column(name = "masked_label", length = 120, nullable = false)
    private String maskedLabel;

    @Column(name = "bank_name", length = 120)
    private String bankName;

    @Column(name = "account_type", length = 32)
    private String accountType;

    @Column(name = "account_number_masked", length = 64, nullable = false)
    private String accountNumberMasked;

    @Column(name = "last4", length = 4)
    private String last4;

    @Column(name = "holder_name", length = 160)
    private String holderName;

    @Column(name = "country", length = 2)
    private String country;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "provider_token_ref", length = 128)
    private String providerTokenRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum DestType { BANK_ACCOUNT }

    public PayoutDestinationJpa() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public DestType getType() { return type; }
    public void setType(DestType type) { this.type = type; }

    public String getMaskedLabel() { return maskedLabel; }
    public void setMaskedLabel(String maskedLabel) { this.maskedLabel = maskedLabel; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }

    public String getAccountNumberMasked() { return accountNumberMasked; }
    public void setAccountNumberMasked(String accountNumberMasked) { this.accountNumberMasked = accountNumberMasked; }

    public String getLast4() { return last4; }
    public void setLast4(String last4) { this.last4 = last4; }

    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public String getProviderTokenRef() { return providerTokenRef; }
    public void setProviderTokenRef(String providerTokenRef) { this.providerTokenRef = providerTokenRef; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
