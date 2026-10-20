package com.micropay.backend.infrastructure.persistence.jpa.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Entidad JPA ↔ tabla `subscriptions` (T20). */
@Entity
@Table(name = "subscriptions")
public class SubscriptionJpa {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "plan_id", columnDefinition = "uuid", nullable = false)
    private UUID planId;

    @Column(name = "subscriber_id", columnDefinition = "uuid", nullable = false)
    private UUID subscriberId;

    @Column(name = "subscriber_wallet_id", columnDefinition = "uuid", nullable = false)
    private UUID subscriberWalletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private SubscriptionStatusJpa status;

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "next_billing_date", nullable = false)
    private Instant nextBillingDate;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "dunning_attempts", nullable = false)
    private int dunningAttempts;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "last_billing_result")
    private String lastBillingResult;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum SubscriptionStatusJpa { TRIALING, ACTIVE, PAST_DUE, CANCELED, PAUSED }

    public SubscriptionJpa() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }

    public UUID getSubscriberId() { return subscriberId; }
    public void setSubscriberId(UUID subscriberId) { this.subscriberId = subscriberId; }

    public UUID getSubscriberWalletId() { return subscriberWalletId; }
    public void setSubscriberWalletId(UUID subscriberWalletId) { this.subscriberWalletId = subscriberWalletId; }

    public SubscriptionStatusJpa getStatus() { return status; }
    public void setStatus(SubscriptionStatusJpa status) { this.status = status; }

    public Instant getCurrentPeriodStart() { return currentPeriodStart; }
    public void setCurrentPeriodStart(Instant currentPeriodStart) { this.currentPeriodStart = currentPeriodStart; }

    public Instant getNextBillingDate() { return nextBillingDate; }
    public void setNextBillingDate(Instant nextBillingDate) { this.nextBillingDate = nextBillingDate; }

    public Instant getCanceledAt() { return canceledAt; }
    public void setCanceledAt(Instant canceledAt) { this.canceledAt = canceledAt; }

    public int getDunningAttempts() { return dunningAttempts; }
    public void setDunningAttempts(int dunningAttempts) { this.dunningAttempts = dunningAttempts; }

    public String getLastBillingResult() { return lastBillingResult; }
    public void setLastBillingResult(String lastBillingResult) { this.lastBillingResult = lastBillingResult; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
