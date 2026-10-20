package com.micropay.backend.domain.entities;

import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.valueobjects.BillingInterval;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.SubscriptionStatus;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionTest {

    @Test
    void start_uses_trial_when_plan_has_trial_days() {
        Plan trialPlan = planBuilder().trialDays(14).build();

        Subscription subscription = Subscription.start(trialPlan, UserId.generate(), WalletId.generate());

        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.TRIALING);
        assertThat(subscription.isBillable()).isTrue();
        assertThat(subscription.nextBillingDate()).isAfter(subscription.currentPeriodStart());
    }

    @Test
    void start_without_trial_begins_active() {
        Plan plan = planBuilder().interval(BillingInterval.WEEK).intervalCount(2).build();

        Subscription subscription = Subscription.start(plan, UserId.generate(), WalletId.generate());

        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.isBillable()).isTrue();
        assertThat(subscription.nextBillingDate()).isAfter(subscription.currentPeriodStart());
    }

    @Test
    void build_requires_next_billing_after_current_period_start() {
        Instant now = Instant.parse("2026-10-20T10:15:30Z");

        assertThatThrownBy(() -> Subscription.builder()
                .planId(planBuilder().build().id())
                .subscriberUserId(UserId.generate())
                .subscriberWalletId(WalletId.generate())
                .currentPeriodStart(now)
                .nextBillingDate(now)
                .build())
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("nextBillingDate");
    }

    @Test
    void due_and_advance_period_reset_dunning_and_restore_active_status() {
        Instant start = Instant.parse("2026-10-01T00:00:00Z");
        Instant next = start.plusSeconds(86_400);
        Subscription subscription = baseSubscription(start, next, SubscriptionStatus.ACTIVE);

        subscription.registerBillingFailure("retry", next.plusSeconds(3_600));
        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(subscription.dunningAttempts()).isEqualTo(1);

        subscription.advancePeriod(BillingInterval.DAY, 1);

        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.dunningAttempts()).isZero();
        assertThat(subscription.lastBillingResult()).isEqualTo("SUCCESS");
        assertThat(subscription.currentPeriodStart()).isEqualTo(next.plusSeconds(3_600));
        assertThat(subscription.nextBillingDate()).isEqualTo(next.plusSeconds(3_600 + 86_400));
    }

    @Test
    void register_billing_failure_updates_status_attempts_and_next_attempt() {
        Instant start = Instant.parse("2026-10-01T00:00:00Z");
        Instant next = start.plusSeconds(86_400);
        Instant retryAt = next.plusSeconds(7_200);
        Subscription subscription = baseSubscription(start, next, SubscriptionStatus.ACTIVE);

        subscription.registerBillingFailure("insufficient_funds", retryAt);

        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(subscription.dunningAttempts()).isEqualTo(1);
        assertThat(subscription.lastBillingResult()).isEqualTo("insufficient_funds");
        assertThat(subscription.currentPeriodStart()).isEqualTo(next);
        assertThat(subscription.nextBillingDate()).isEqualTo(retryAt);
        assertThat(subscription.isDueAt(retryAt.minusSeconds(1))).isFalse();
    }

    @Test
    void pause_resume_and_cancel_enforce_valid_transitions() {
        Instant start = Instant.parse("2026-10-01T00:00:00Z");
        Instant next = start.plusSeconds(86_400);
        Subscription subscription = baseSubscription(start, next, SubscriptionStatus.ACTIVE);

        assertThat(subscription.isDueAt(next)).isTrue();

        subscription.pause();
        subscription.pause();
        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.PAUSED);
        assertThat(subscription.isBillable()).isFalse();

        subscription.resume();
        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.ACTIVE);

        subscription.cancel();
        subscription.cancel();
        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(subscription.canceledAt()).isNotNull();

        assertThatThrownBy(subscription::pause)
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Solo ACTIVE/TRIALING puede pausarse");

        assertThatThrownBy(subscription::resume)
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Solo PAUSED puede reanudarse");
    }

    private Subscription baseSubscription(Instant currentPeriodStart, Instant nextBillingDate,
                                          SubscriptionStatus status) {
        return Subscription.builder()
                .planId(planBuilder().build().id())
                .subscriberUserId(UserId.generate())
                .subscriberWalletId(WalletId.generate())
                .status(status)
                .currentPeriodStart(currentPeriodStart)
                .nextBillingDate(nextBillingDate)
                .build();
    }

    private Plan.Builder planBuilder() {
        return Plan.builder()
                .merchantUserId(UserId.generate())
                .name("Plan Base")
                .amount(Money.of(new BigDecimal("19.99"), Currency.USD))
                .interval(BillingInterval.MONTH)
                .intervalCount(1);
    }
}
