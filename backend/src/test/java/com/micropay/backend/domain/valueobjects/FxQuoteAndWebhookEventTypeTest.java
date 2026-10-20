package com.micropay.backend.domain.valueobjects;

import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FxQuoteAndWebhookEventTypeTest {

    @Test
    void fx_quote_requires_positive_rate_and_matching_base_currency() {
        assertThatThrownBy(() -> new FxQuote(Currency.USD, Currency.COP, BigDecimal.ZERO, "stub", Instant.now()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("tipo de cambio debe ser > 0");

        FxQuote quote = new FxQuote(Currency.USD, Currency.COP, new BigDecimal("4100"), "stub", null);
        Money converted = quote.convert(Money.of(new BigDecimal("1.25"), Currency.USD));

        assertThat(converted.currency()).isEqualTo(Currency.COP);
        assertThat(converted.amount()).isEqualByComparingTo("5125");
        assertThat(quote.fetchedAt()).isNotNull();

        assertThatThrownBy(() -> quote.convert(Money.of(new BigDecimal("1.25"), Currency.EUR)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("cotización es USD→COP");
    }

    @Test
    void webhook_event_type_helpers_cover_masks_known_values_and_parse() {
        assertThat(WebhookEventType.covers(List.of("*"), "TOPUP_FAILED")).isTrue();
        assertThat(WebhookEventType.covers(List.of("payment_request_paid"), "PAYMENT_REQUEST_PAID")).isTrue();
        assertThat(WebhookEventType.covers(List.of("PAYMENT_REQUEST_PAID"), "TOPUP_FAILED")).isFalse();
        assertThat(WebhookEventType.covers(null, "TOPUP_FAILED")).isFalse();

        assertThat(WebhookEventType.isKnown("topup_failed")).isTrue();
        assertThat(WebhookEventType.isKnown("")).isFalse();
        assertThat(WebhookEventType.parse("topup_failed")).isEqualTo(WebhookEventType.TOPUP_FAILED);

        assertThatThrownBy(() -> WebhookEventType.parse("unknown_event"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Evento de webhook desconocido");
    }
}
