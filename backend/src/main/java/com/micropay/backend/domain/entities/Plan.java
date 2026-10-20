package com.micropay.backend.domain.entities;

import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.valueobjects.BillingInterval;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.PlanId;
import com.micropay.backend.domain.valueobjects.PlanStatus;
import com.micropay.backend.domain.valueobjects.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Plan — plantilla de cobro recurrente que ofrece un comercio (Requirement 22.1).
 *
 * INVARIANTES:
 *   BR#01: amount > 0 y currency presente.
 *   BR#02: intervalCount >= 1.
 *   BR#03: name no vacío (<= 255).
 *   BR#04: trialDays, si presente, >= 0.
 *   BR#05: un Plan ARCHIVED no acepta nuevas suscripciones (validado en SubscriptionService).
 */
public final class Plan {

    private static final int MAX_NAME_LEN = 255;

    private final PlanId id;
    private final UserId merchantUserId;
    private final String name;
    private final Money amount;
    private final BillingInterval interval;
    private final int intervalCount;
    private final Integer trialDays;
    private final Instant createdAt;
    private PlanStatus status;

    private Plan(Builder b) {
        if (b.merchantUserId == null) throw new IllegalArgumentException("merchantUserId es requerido");
        if (b.name == null || b.name.isBlank()) {
            throw new BusinessRuleViolationException("PLAN_NAME_INVALID", "El nombre del plan es obligatorio");
        }
        if (b.name.length() > MAX_NAME_LEN) {
            throw new BusinessRuleViolationException("PLAN_NAME_TOO_LONG",
                    "El nombre excede %d caracteres".formatted(MAX_NAME_LEN));
        }
        if (b.amount == null || !b.amount.isPositive()) {
            throw new BusinessRuleViolationException("PLAN_AMOUNT_INVALID", "El monto del plan debe ser > 0");
        }
        if (b.interval == null) throw new IllegalArgumentException("interval es requerido");
        if (b.intervalCount < 1) {
            throw new BusinessRuleViolationException("PLAN_INTERVAL_COUNT_INVALID",
                    "intervalCount debe ser >= 1, recibió " + b.intervalCount);
        }
        if (b.trialDays != null && b.trialDays < 0) {
            throw new BusinessRuleViolationException("PLAN_TRIAL_DAYS_INVALID",
                    "trialDays debe ser >= 0, recibió " + b.trialDays);
        }
        this.id = b.id == null ? PlanId.generate() : b.id;
        this.merchantUserId = b.merchantUserId;
        this.name = b.name;
        this.amount = b.amount;
        this.interval = b.interval;
        this.intervalCount = b.intervalCount;
        this.trialDays = b.trialDays;
        this.createdAt = b.createdAt == null ? Instant.now() : b.createdAt;
        this.status = b.status == null ? PlanStatus.ACTIVE : b.status;
    }

    public static Builder builder() { return new Builder(); }

    // ────────── Comportamiento ──────────

    /** Archiva el plan: no admite nuevas suscripciones (idempotente). */
    public void archive() {
        this.status = PlanStatus.ARCHIVED;
    }

    /** Reactiva un plan archivado (idempotente). */
    public void activate() {
        this.status = PlanStatus.ACTIVE;
    }

    public boolean isActive() { return status == PlanStatus.ACTIVE; }

    public boolean hasTrial() { return trialDays != null && trialDays > 0; }

    // ────────── Getters ──────────

    public PlanId id() { return id; }
    public UserId merchantUserId() { return merchantUserId; }
    public String name() { return name; }
    public Money amount() { return amount; }
    public BillingInterval interval() { return interval; }
    public int intervalCount() { return intervalCount; }
    public Integer trialDays() { return trialDays; }
    public PlanStatus status() { return status; }
    public Instant createdAt() { return createdAt; }

    // ────────── Builder ──────────

    public static final class Builder {
        private PlanId id;
        private UserId merchantUserId;
        private String name;
        private Money amount;
        private BillingInterval interval;
        private int intervalCount = 1;
        private Integer trialDays;
        private PlanStatus status;
        private Instant createdAt;

        public Builder id(PlanId v) { this.id = v; return this; }
        public Builder merchantUserId(UserId v) { this.merchantUserId = v; return this; }
        public Builder name(String v) { this.name = v; return this; }
        public Builder amount(Money v) { this.amount = v; return this; }
        public Builder interval(BillingInterval v) { this.interval = v; return this; }
        public Builder intervalCount(int v) { this.intervalCount = v; return this; }
        public Builder trialDays(Integer v) { this.trialDays = v; return this; }
        public Builder status(PlanStatus v) { this.status = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }

        public Plan build() { return new Plan(this); }
    }
}
