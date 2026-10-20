package com.micropay.backend.domain.entities;

import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.PaymentRequestStatus;
import com.micropay.backend.domain.valueobjects.ShortCode;
import com.micropay.backend.domain.valueobjects.TransactionId;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentRequestTest {

    @Test
    void build_sets_default_expiry_and_rejects_invalid_expiry() {
        Instant createdAt = Instant.parse("2026-10-20T10:15:30Z");
        PaymentRequest request = baseBuilder()
                .createdAt(createdAt)
                .build();

        assertThat(request.status()).isEqualTo(PaymentRequestStatus.PENDING);
        assertThat(request.expiresAt()).isEqualTo(createdAt.plus(PaymentRequest.DEFAULT_EXPIRY));

        assertThatThrownBy(() -> baseBuilder()
                .createdAt(createdAt)
                .expiresAt(createdAt)
                .build())
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("expiresAt debe ser posterior");
    }

    @Test
    void mark_paid_is_idempotent_for_same_transaction_and_blocks_double_pay() {
        PaymentRequest request = baseBuilder().build();
        TransactionId txId = TransactionId.generate();

        request.markPaid(txId);
        Instant paidAt = request.paidAt();
        request.markPaid(txId);

        assertThat(request.status()).isEqualTo(PaymentRequestStatus.PAID);
        assertThat(request.paidWithTxId()).isEqualTo(txId);
        assertThat(request.paidAt()).isEqualTo(paidAt);

        assertThatThrownBy(() -> request.markPaid(TransactionId.generate()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("ya fue pagada");
    }

    @Test
    void expired_and_cancelled_transitions_only_apply_from_pending() {
        PaymentRequest expired = baseBuilder().build();
        expired.markExpired();
        expired.markExpired();
        assertThat(expired.status()).isEqualTo(PaymentRequestStatus.EXPIRED);
        assertThat(expired.isTerminal()).isTrue();

        PaymentRequest cancelled = baseBuilder().build();
        cancelled.markCancelled();
        cancelled.markCancelled();
        assertThat(cancelled.status()).isEqualTo(PaymentRequestStatus.CANCELLED);
        assertThat(cancelled.isTerminal()).isTrue();

        PaymentRequest paid = baseBuilder().build();
        paid.markPaid(TransactionId.generate());
        assertThatThrownBy(paid::markExpired)
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("No se puede marcar EXPIRED");
        assertThatThrownBy(paid::markCancelled)
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Solo PENDING puede cancelarse");
    }

    @Test
    void refunded_transition_only_applies_from_paid() {
        PaymentRequest request = baseBuilder().build();

        assertThatThrownBy(request::markRefunded)
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Solo PAID puede pasar a REFUNDED");

        request.markPaid(TransactionId.generate());
        request.markRefunded();
        request.markRefunded();

        assertThat(request.status()).isEqualTo(PaymentRequestStatus.REFUNDED);
        assertThat(request.isTerminal()).isTrue();
    }

    @Test
    void helper_methods_report_pending_and_expiry_consistently() {
        Instant createdAt = Instant.parse("2026-10-20T10:15:30Z");
        PaymentRequest request = baseBuilder()
                .createdAt(createdAt)
                .expiresAt(createdAt.plusSeconds(60))
                .build();

        assertThat(request.isPending()).isTrue();
        assertThat(request.isExpiredAt(createdAt.plusSeconds(59))).isFalse();
        assertThat(request.isExpiredAt(createdAt.plusSeconds(60))).isTrue();
    }

    private PaymentRequest.Builder baseBuilder() {
        return PaymentRequest.builder()
                .merchantUserId(UserId.generate())
                .merchantWalletId(WalletId.generate())
                .amount(Money.of(new BigDecimal("120.00"), Currency.USD))
                .description("Pedido")
                .shortCode(new ShortCode("ABC123"));
    }
}
