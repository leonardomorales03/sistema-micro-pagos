package com.micropay.backend.domain.entities;

import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.valueobjects.BillingInterval;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.PlanStatus;
import com.micropay.backend.domain.valueobjects.UserId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanTest {

    @Test
    void build_applies_defaults_and_validates_required_fields() {
        Plan plan = baseBuilder().build();

        assertThat(plan.status()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(plan.intervalCount()).isEqualTo(1);
        assertThat(plan.isActive()).isTrue();
        assertThat(plan.hasTrial()).isFalse();

        assertThatThrownBy(() -> baseBuilder().name(" ").build())
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("nombre del plan es obligatorio");

        assertThatThrownBy(() -> baseBuilder()
                .amount(Money.zero(Currency.USD))
                .build())
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("monto del plan debe ser > 0");

        assertThatThrownBy(() -> baseBuilder().intervalCount(0).build())
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("intervalCount debe ser >= 1");

        assertThatThrownBy(() -> baseBuilder().trialDays(-1).build())
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("trialDays debe ser >= 0");
    }

    @Test
    void archive_and_activate_are_idempotent() {
        Plan plan = baseBuilder().trialDays(7).build();

        assertThat(plan.hasTrial()).isTrue();

        plan.archive();
        plan.archive();
        assertThat(plan.status()).isEqualTo(PlanStatus.ARCHIVED);

        plan.activate();
        plan.activate();
        assertThat(plan.status()).isEqualTo(PlanStatus.ACTIVE);
    }

    private Plan.Builder baseBuilder() {
        return Plan.builder()
                .merchantUserId(UserId.generate())
                .name("Plan Pro")
                .amount(Money.of(new BigDecimal("29.99"), Currency.USD))
                .interval(BillingInterval.MONTH);
    }
}
