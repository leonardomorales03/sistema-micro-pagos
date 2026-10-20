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

/** Entidad JPA ↔ tabla `plans` (T20). */
@Entity
@Table(name = "plans")
public class PlanJpa {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "merchant_user_id", columnDefinition = "uuid", nullable = false)
    private UUID merchantUserId;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "amount", precision = 24, scale = 8, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", length = 10, nullable = false)
    private BillingIntervalJpa billingInterval;

    @Column(name = "interval_count", nullable = false)
    private int intervalCount;

    @Column(name = "trial_days")
    private Integer trialDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private PlanStatusJpa status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public enum BillingIntervalJpa { DAY, WEEK, MONTH, YEAR }

    public enum PlanStatusJpa { ACTIVE, ARCHIVED }

    public PlanJpa() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getMerchantUserId() { return merchantUserId; }
    public void setMerchantUserId(UUID merchantUserId) { this.merchantUserId = merchantUserId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BillingIntervalJpa getBillingInterval() { return billingInterval; }
    public void setBillingInterval(BillingIntervalJpa billingInterval) { this.billingInterval = billingInterval; }

    public int getIntervalCount() { return intervalCount; }
    public void setIntervalCount(int intervalCount) { this.intervalCount = intervalCount; }

    public Integer getTrialDays() { return trialDays; }
    public void setTrialDays(Integer trialDays) { this.trialDays = trialDays; }

    public PlanStatusJpa getStatus() { return status; }
    public void setStatus(PlanStatusJpa status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
