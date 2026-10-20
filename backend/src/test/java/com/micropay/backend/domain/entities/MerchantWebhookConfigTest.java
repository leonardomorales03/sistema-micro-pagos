package com.micropay.backend.domain.entities;

import com.micropay.backend.domain.valueobjects.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MerchantWebhookConfigTest {

    @Test
    void build_requires_valid_endpoint_and_secret() {
        assertThatThrownBy(() -> baseBuilder().endpointUrl("ftp://example.com").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http:// o https://");

        assertThatThrownBy(() -> baseBuilder().signingSecret(" ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signingSecret es requerido");
    }

    @Test
    void create_defaults_to_active_and_wildcard_subscription() {
        MerchantWebhookConfig config = MerchantWebhookConfig.create(
                UserId.generate(), "https://merchant.example/webhook", "secret", List.of()
        );

        assertThat(config.active()).isTrue();
        assertThat(config.subscribedEvents()).containsExactly("*");
    }

    @Test
    void update_rotate_and_activation_helpers_work() {
        Instant createdAt = Instant.parse("2026-10-20T10:15:30Z");
        MerchantWebhookConfig config = baseBuilder()
                .createdAt(createdAt)
                .subscribedEvents(List.of("payment_request_paid", "payment_request_paid", " "))
                .build();

        assertThat(config.subscribedEvents()).containsExactly("PAYMENT_REQUEST_PAID");

        config.update("https://merchant.example/v2", List.of("withdrawal_completed"));
        assertThat(config.endpointUrl()).isEqualTo("https://merchant.example/v2");
        assertThat(config.subscribedEvents()).containsExactly("WITHDRAWAL_COMPLETED");

        config.rotateSecret("secret-v2");
        assertThat(config.signingSecret()).isEqualTo("secret-v2");

        config.deactivate();
        assertThat(config.active()).isFalse();
        config.activate();
        assertThat(config.active()).isTrue();
        assertThat(config.updatedAt()).isAfterOrEqualTo(createdAt);
    }

    @Test
    void subscription_mask_checks_specific_and_wildcard_events() {
        MerchantWebhookConfig config = baseBuilder()
                .subscribedEvents(List.of("PAYMENT_REQUEST_PAID"))
                .build();

        assertThat(config.isSubscribedTo("PAYMENT_REQUEST_PAID")).isTrue();
        assertThat(config.isSubscribedTo("TOPUP_FAILED")).isFalse();

        MerchantWebhookConfig wildcard = baseBuilder()
                .subscribedEvents(List.of("*"))
                .build();
        assertThat(wildcard.isSubscribedTo("TOPUP_FAILED")).isTrue();
    }

    private MerchantWebhookConfig.Builder baseBuilder() {
        return MerchantWebhookConfig.builder()
                .merchantUserId(UserId.generate())
                .endpointUrl("https://merchant.example/webhook")
                .signingSecret("secret");
    }
}
