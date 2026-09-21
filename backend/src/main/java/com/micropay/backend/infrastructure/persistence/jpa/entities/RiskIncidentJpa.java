package com.micropay.backend.infrastructure.persistence.jpa.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "risk_incidents")
public class RiskIncidentJpa {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "rule", length = 64, nullable = false)
    private String rule;

    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "wallet_id", columnDefinition = "uuid")
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 8, nullable = false)
    private RiskSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private RiskStatus status;

    @Column(name = "ip_address", columnDefinition = "inet")
    private InetAddress ipAddress;

    @Column(name = "device_fingerprint", length = 64)
    private String deviceFingerprint;

    @Lob
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_by_rule_at", nullable = false)
    private Instant createdByRuleAt;

    @Column(name = "reviewed_by", columnDefinition = "uuid")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "resolution_note", length = 512)
    private String resolutionNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum RiskSeverity { LOW, MEDIUM, HIGH, CRITICAL }

    public enum RiskStatus { OPEN, INVESTIGATING, RESOLVED, DISMISSED }

    public RiskIncidentJpa() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getRule() { return rule; }
    public void setRule(String rule) { this.rule = rule; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getWalletId() { return walletId; }
    public void setWalletId(UUID walletId) { this.walletId = walletId; }

    public RiskSeverity getSeverity() { return severity; }
    public void setSeverity(RiskSeverity severity) { this.severity = severity; }

    public RiskStatus getStatus() { return status; }
    public void setStatus(RiskStatus status) { this.status = status; }

    public InetAddress getIpAddress() { return ipAddress; }
    public void setIpAddress(InetAddress ipAddress) { this.ipAddress = ipAddress; }

    public String getDeviceFingerprint() { return deviceFingerprint; }
    public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public Instant getCreatedByRuleAt() { return createdByRuleAt; }
    public void setCreatedByRuleAt(Instant createdByRuleAt) { this.createdByRuleAt = createdByRuleAt; }

    public UUID getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(UUID reviewedBy) { this.reviewedBy = reviewedBy; }

    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }

    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
