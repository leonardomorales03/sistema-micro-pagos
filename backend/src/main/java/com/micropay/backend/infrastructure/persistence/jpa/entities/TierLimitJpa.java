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
@Table(name = "tier_limits")
public class TierLimitJpa {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_level", length = 16, nullable = false)
    private KycLevelEnum kycLevel;

    @Column(name = "country", length = 2, nullable = false)
    private String country;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "max_single_tx", nullable = false, precision = 24, scale = 8)
    private BigDecimal maxSingleTx;

    @Column(name = "max_daily_volume", nullable = false, precision = 24, scale = 8)
    private BigDecimal maxDailyVolume;

    @Column(name = "max_monthly_volume", nullable = false, precision = 24, scale = 8)
    private BigDecimal maxMonthlyVolume;

    @Column(name = "max_wallet_balance", nullable = false, precision = 24, scale = 8)
    private BigDecimal maxWalletBalance;

    @Column(name = "max_topups_per_day", nullable = false)
    private int maxTopupsPerDay;

    @Column(name = "max_withdrawals_per", nullable = false)
    private int maxWithdrawalsPer;

    @Column(name = "withdrawal_tier_fee", nullable = false, precision = 8, scale = 4)
    private BigDecimal withdrawalTierFee;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum KycLevelEnum { LEVEL_0, LEVEL_1, LEVEL_2 }

    public TierLimitJpa() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public KycLevelEnum getKycLevel() { return kycLevel; }
    public void setKycLevel(KycLevelEnum kycLevel) { this.kycLevel = kycLevel; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getMaxSingleTx() { return maxSingleTx; }
    public void setMaxSingleTx(BigDecimal maxSingleTx) { this.maxSingleTx = maxSingleTx; }

    public BigDecimal getMaxDailyVolume() { return maxDailyVolume; }
    public void setMaxDailyVolume(BigDecimal maxDailyVolume) { this.maxDailyVolume = maxDailyVolume; }

    public BigDecimal getMaxMonthlyVolume() { return maxMonthlyVolume; }
    public void setMaxMonthlyVolume(BigDecimal maxMonthlyVolume) { this.maxMonthlyVolume = maxMonthlyVolume; }

    public BigDecimal getMaxWalletBalance() { return maxWalletBalance; }
    public void setMaxWalletBalance(BigDecimal maxWalletBalance) { this.maxWalletBalance = maxWalletBalance; }

    public int getMaxTopupsPerDay() { return maxTopupsPerDay; }
    public void setMaxTopupsPerDay(int maxTopupsPerDay) { this.maxTopupsPerDay = maxTopupsPerDay; }

    public int getMaxWithdrawalsPer() { return maxWithdrawalsPer; }
    public void setMaxWithdrawalsPer(int maxWithdrawalsPer) { this.maxWithdrawalsPer = maxWithdrawalsPer; }

    public BigDecimal getWithdrawalTierFee() { return withdrawalTierFee; }
    public void setWithdrawalTierFee(BigDecimal withdrawalTierFee) { this.withdrawalTierFee = withdrawalTierFee; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
