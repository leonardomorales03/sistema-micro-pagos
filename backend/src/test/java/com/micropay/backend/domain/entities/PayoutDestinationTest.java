package com.micropay.backend.domain.entities;

import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.valueobjects.UserId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayoutDestinationTest {

    @Test
    void build_requires_masked_fields() {
        assertThatThrownBy(() -> baseBuilder().maskedLabel(" ").build())
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("maskedLabel");

        assertThatThrownBy(() -> baseBuilder().accountNumberMasked(" ").build())
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("accountNumberMasked");
    }

    @Test
    void helpers_mask_and_extract_last4() {
        assertThat(PayoutDestination.last4Of("1234-5678-9012-3456")).isEqualTo("3456");
        assertThat(PayoutDestination.last4Of("999")).isEqualTo("999");
        assertThat(PayoutDestination.last4Of(null)).isNull();

        assertThat(PayoutDestination.mask("12345678", "3456")).isEqualTo("**** **** **** 3456");
        assertThat(PayoutDestination.mask("12345678", null)).isEqualTo("**** **** **** ****");
    }

    @Test
    void mark_verified_set_default_and_update_label_mutate_state() {
        PayoutDestination destination = baseBuilder().build();

        destination.markVerified();
        destination.setDefault(true);
        destination.updateLabel("Cuenta principal", "Banco Uno", "SAVINGS", "Leo Morales");

        assertThat(destination.verified()).isTrue();
        assertThat(destination.isDefault()).isTrue();
        assertThat(destination.maskedLabel()).isEqualTo("Cuenta principal");
        assertThat(destination.bankName()).isEqualTo("Banco Uno");
        assertThat(destination.accountType()).isEqualTo("SAVINGS");
        assertThat(destination.holderName()).isEqualTo("Leo Morales");
    }

    private PayoutDestination.Builder baseBuilder() {
        return PayoutDestination.builder()
                .userId(UserId.generate())
                .maskedLabel("Cuenta **** 3456")
                .accountNumberMasked("**** **** **** 3456")
                .last4("3456");
    }
}
