package com.micropay.backend.infrastructure.persistence.jpa.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA de {@code reconciliation_runs} (V11, T25).
 */
@Entity
@Table(name = "reconciliation_runs")
public class ReconciliationRunJpa {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "triggered_by", columnDefinition = "uuid")
    private UUID triggeredBy;

    @Column(name = "provider", length = 40, nullable = false)
    private String provider;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "period_start")
    private Instant periodStart;

    @Column(name = "period_end")
    private Instant periodEnd;

    @Column(name = "internal_count", nullable = false)
    private int internalCount;

    @Column(name = "provider_count", nullable = false)
    private int providerCount;

    @Column(name = "mismatch_count", nullable = false)
    private int mismatchCount;

    @Column(name = "notes", length = 512)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public ReconciliationRunJpa() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(UUID triggeredBy) { this.triggeredBy = triggeredBy; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getPeriodStart() { return periodStart; }
    public void setPeriodStart(Instant periodStart) { this.periodStart = periodStart; }

    public Instant getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(Instant periodEnd) { this.periodEnd = periodEnd; }

    public int getInternalCount() { return internalCount; }
    public void setInternalCount(int internalCount) { this.internalCount = internalCount; }

    public int getProviderCount() { return providerCount; }
    public void setProviderCount(int providerCount) { this.providerCount = providerCount; }

    public int getMismatchCount() { return mismatchCount; }
    public void setMismatchCount(int mismatchCount) { this.mismatchCount = mismatchCount; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
