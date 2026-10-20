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

/**
 * Entidad JPA de la tabla {@code referrals} (V13). Debe casar EXACTAMENTE con la migración
 * ({@code ddl-auto: validate}).
 */
@Entity
@Table(name = "referrals")
public class ReferralJpa {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "code", length = 16, nullable = false)
    private String code;

    @Column(name = "referrer_id", columnDefinition = "uuid", nullable = false)
    private UUID referrerId;

    @Column(name = "invitee_id", columnDefinition = "uuid")
    private UUID inviteeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ReferralStatus status;

    @Column(name = "referrer_bonus", precision = 19, scale = 4)
    private BigDecimal referrerBonus;

    @Column(name = "invitee_bonus", precision = 19, scale = 4)
    private BigDecimal inviteeBonus;

    @Column(name = "bonus_tx_id", columnDefinition = "uuid")
    private UUID bonusTxId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "qualified_at")
    private Instant qualifiedAt;

    public enum ReferralStatus { PENDING, QUALIFIED, EXPIRED }

    public ReferralJpa() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public UUID getReferrerId() { return referrerId; }
    public void setReferrerId(UUID referrerId) { this.referrerId = referrerId; }

    public UUID getInviteeId() { return inviteeId; }
    public void setInviteeId(UUID inviteeId) { this.inviteeId = inviteeId; }

    public ReferralStatus getStatus() { return status; }
    public void setStatus(ReferralStatus status) { this.status = status; }

    public BigDecimal getReferrerBonus() { return referrerBonus; }
    public void setReferrerBonus(BigDecimal referrerBonus) { this.referrerBonus = referrerBonus; }

    public BigDecimal getInviteeBonus() { return inviteeBonus; }
    public void setInviteeBonus(BigDecimal inviteeBonus) { this.inviteeBonus = inviteeBonus; }

    public UUID getBonusTxId() { return bonusTxId; }
    public void setBonusTxId(UUID bonusTxId) { this.bonusTxId = bonusTxId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getQualifiedAt() { return qualifiedAt; }
    public void setQualifiedAt(Instant qualifiedAt) { this.qualifiedAt = qualifiedAt; }
}
