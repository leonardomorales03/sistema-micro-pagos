package com.micropay.backend.infrastructure.persistence.jpa.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA de {@code reconciliation_mismatches} (V11, T25).
 */
@Entity
@Table(name = "reconciliation_mismatches")
public class ReconciliationMismatchJpa {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "run_id", columnDefinition = "uuid", nullable = false)
    private UUID runId;

    @Column(name = "kind", length = 40, nullable = false)
    private String kind;

    @Column(name = "internal_tx_id", columnDefinition = "uuid")
    private UUID internalTxId;

    @Column(name = "provider_ref", length = 64)
    private String providerRef;

    @Column(name = "internal_amount", precision = 24, scale = 8)
    private BigDecimal internalAmount;

    @Column(name = "provider_amount", precision = 24, scale = 8)
    private BigDecimal providerAmount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "resolution_note", length = 512)
    private String resolutionNote;

    @Column(name = "resolved_by", columnDefinition = "uuid")
    private UUID resolvedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ReconciliationMismatchJpa() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public UUID getInternalTxId() { return internalTxId; }
    public void setInternalTxId(UUID internalTxId) { this.internalTxId = internalTxId; }

    public String getProviderRef() { return providerRef; }
    public void setProviderRef(String providerRef) { this.providerRef = providerRef; }

    public BigDecimal getInternalAmount() { return internalAmount; }
    public void setInternalAmount(BigDecimal internalAmount) { this.internalAmount = internalAmount; }

    public BigDecimal getProviderAmount() { return providerAmount; }
    public void setProviderAmount(BigDecimal providerAmount) { this.providerAmount = providerAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }

    public UUID getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(UUID resolvedBy) { this.resolvedBy = resolvedBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
