package com.micropay.backend.domain.entities;

import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.valueobjects.BillingInterval;
import com.micropay.backend.domain.valueobjects.PlanId;
import com.micropay.backend.domain.valueobjects.SubscriptionId;
import com.micropay.backend.domain.valueobjects.SubscriptionStatus;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;

import java.time.Instant;
import java.util.Objects;

/**
 * Subscription — mandato de cobro recurrente de un suscriptor sobre un Plan (Requirement 22).
 *
 * Ciclo:
 *   (trial) TRIALING ──┐
 *   (sin trial) ACTIVE ┼──(cobro OK)──> ACTIVE (avanza nextBillingDate)
 *                      ├──(cobro falla)──> PAST_DUE (dunning) ──(agota)──> CANCELED
 *                      ├──(pause)──> PAUSED ──(resume)──> ACTIVE
 *                      └──(cancel)──> CANCELED
 *
 * INVARIANTES:
 *   BR#01: nextBillingDate > currentPeriodStart.
 *   BR#02: dunningAttempts >= 0.
 *   BR#03: transiciones de estado válidas.
 */
public final class Subscription {

    private final SubscriptionId id;
    private final PlanId planId;
    private final UserId subscriberUserId;
    private final WalletId subscriberWalletId;
    private final Instant createdAt;
    private SubscriptionStatus status;
    private Instant currentPeriodStart;
    private Instant nextBillingDate;
    private Instant canceledAt;
    private int dunningAttempts;
    private String lastBillingResult;

    private Subscription(Builder b) {
        if (b.planId == null) throw new IllegalArgumentException("planId es requerido");
        if (b.subscriberUserId == null) throw new IllegalArgumentException("subscriberUserId es requerido");
        if (b.subscriberWalletId == null) throw new IllegalArgumentException("subscriberWalletId es requerido");
        this.id = b.id == null ? SubscriptionId.generate() : b.id;
        this.planId = b.planId;
        this.subscriberUserId = b.subscriberUserId;
        this.subscriberWalletId = b.subscriberWalletId;
        this.createdAt = b.createdAt == null ? Instant.now() : b.createdAt;
        this.currentPeriodStart = b.currentPeriodStart == null ? this.createdAt : b.currentPeriodStart;
        this.nextBillingDate = b.nextBillingDate;
        this.status = b.status == null ? SubscriptionStatus.ACTIVE : b.status;
        this.canceledAt = b.canceledAt;
        this.dunningAttempts = Math.max(0, b.dunningAttempts);
        this.lastBillingResult = b.lastBillingResult;
        if (this.nextBillingDate == null) {
            throw new IllegalArgumentException("nextBillingDate es requerido");
        }
        if (!this.nextBillingDate.isAfter(this.currentPeriodStart)) {
            throw new BusinessRuleViolationException("SUBSCRIPTION_BILLING_DATE_INVALID",
                    "nextBillingDate %s debe ser posterior a currentPeriodStart %s"
                            .formatted(this.nextBillingDate, this.currentPeriodStart));
        }
    }

    public static Builder builder() { return new Builder(); }

    /**
     * Crea una suscripción a partir de un Plan. Si el plan tiene trialDays > 0 entra en TRIALING
     * con el primer cobro al final del trial; en caso contrario entra ACTIVE.
     */
    public static Subscription start(Plan plan, UserId subscriberUserId, WalletId subscriberWalletId) {
        Objects.requireNonNull(plan, "plan");
        Instant now = Instant.now();
        boolean trial = plan.hasTrial();
        Instant firstBilling = trial
                ? now.plusSeconds(plan.trialDays() * 86_400L)
                : plan.interval().advance(now, plan.intervalCount());
        return Subscription.builder()
                .planId(plan.id())
                .subscriberUserId(subscriberUserId)
                .subscriberWalletId(subscriberWalletId)
                .status(trial ? SubscriptionStatus.TRIALING : SubscriptionStatus.ACTIVE)
                .currentPeriodStart(now)
                .nextBillingDate(firstBilling)
                .dunningAttempts(0)
                .build();
    }

    // ────────── Comportamiento ──────────

    /** true si el estado permite intentar un cobro. */
    public boolean isBillable() {
        return status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.TRIALING;
    }

    public boolean isDueAt(Instant now) {
        return isBillable() && !nextBillingDate.isAfter(now);
    }

    /** Aplica un cobro exitoso: avanza el período y limpia dunning. */
    public void advancePeriod(BillingInterval interval, int intervalCount) {
        this.currentPeriodStart = this.nextBillingDate;
        this.nextBillingDate = interval.advance(this.nextBillingDate, intervalCount);
        this.status = SubscriptionStatus.ACTIVE;
        this.dunningAttempts = 0;
        this.lastBillingResult = "SUCCESS";
    }

    /**
     * Registra un intento de cobro fallido y reprograma el siguiente intento (dunning).
     * No cancela por sí solo; la política de agotamiento la aplica el servicio.
     */
    public void registerBillingFailure(String reason, Instant nextAttempt) {
        this.dunningAttempts++;
        this.lastBillingResult = reason == null ? "FAILED" : reason;
        this.status = SubscriptionStatus.PAST_DUE;
        if (nextAttempt != null) {
            this.currentPeriodStart = this.nextBillingDate;
            this.nextBillingDate = nextAttempt;
        }
    }

    /** Cancela (terminal). Idempotente. */
    public void cancel() {
        if (this.status == SubscriptionStatus.CANCELED) return;
        this.status = SubscriptionStatus.CANCELED;
        this.canceledAt = Instant.now();
    }

    /** Pausa una suscripción activa/en trial (efecto en el siguiente ciclo). Idempotente. */
    public void pause() {
        if (this.status == SubscriptionStatus.PAUSED) return;
        if (this.status != SubscriptionStatus.ACTIVE && this.status != SubscriptionStatus.TRIALING) {
            throw new BusinessRuleViolationException("SUBSCRIPTION_STATUS_TRANSITION_INVALID",
                    "Solo ACTIVE/TRIALING puede pausarse, estado actual " + this.status);
        }
        this.status = SubscriptionStatus.PAUSED;
    }

    /** Reanuda una suscripción pausada. Idempotente si ya ACTIVE. */
    public void resume() {
        if (this.status == SubscriptionStatus.ACTIVE) return;
        if (this.status != SubscriptionStatus.PAUSED) {
            throw new BusinessRuleViolationException("SUBSCRIPTION_STATUS_TRANSITION_INVALID",
                    "Solo PAUSED puede reanudarse, estado actual " + this.status);
        }
        this.status = SubscriptionStatus.ACTIVE;
    }

    // ────────── Getters ──────────

    public SubscriptionId id() { return id; }
    public PlanId planId() { return planId; }
    public UserId subscriberUserId() { return subscriberUserId; }
    public WalletId subscriberWalletId() { return subscriberWalletId; }
    public SubscriptionStatus status() { return status; }
    public Instant currentPeriodStart() { return currentPeriodStart; }
    public Instant nextBillingDate() { return nextBillingDate; }
    public Instant canceledAt() { return canceledAt; }
    public int dunningAttempts() { return dunningAttempts; }
    public String lastBillingResult() { return lastBillingResult; }
    public Instant createdAt() { return createdAt; }

    // ────────── Builder ──────────

    public static final class Builder {
        private SubscriptionId id;
        private PlanId planId;
        private UserId subscriberUserId;
        private WalletId subscriberWalletId;
        private SubscriptionStatus status;
        private Instant currentPeriodStart;
        private Instant nextBillingDate;
        private Instant canceledAt;
        private int dunningAttempts;
        private String lastBillingResult;
        private Instant createdAt;

        public Builder id(SubscriptionId v) { this.id = v; return this; }
        public Builder planId(PlanId v) { this.planId = v; return this; }
        public Builder subscriberUserId(UserId v) { this.subscriberUserId = v; return this; }
        public Builder subscriberWalletId(WalletId v) { this.subscriberWalletId = v; return this; }
        public Builder status(SubscriptionStatus v) { this.status = v; return this; }
        public Builder currentPeriodStart(Instant v) { this.currentPeriodStart = v; return this; }
        public Builder nextBillingDate(Instant v) { this.nextBillingDate = v; return this; }
        public Builder canceledAt(Instant v) { this.canceledAt = v; return this; }
        public Builder dunningAttempts(int v) { this.dunningAttempts = v; return this; }
        public Builder lastBillingResult(String v) { this.lastBillingResult = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }

        public Subscription build() { return new Subscription(this); }
    }
}
