package com.micropay.backend.domain.entities;

import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.PayoutDestinationId;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WithdrawalTest {

    @Test
    void build_rejects_invalid_amounts() {
        assertThatThrownBy(() -> baseBuilder()
                .grossAmount(Money.zero(Currency.USD))
                .build())
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("monto > 0");

        assertThatThrownBy(() -> baseBuilder()
                .netAmount(Money.zero(Currency.USD))
                .build())
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("netAmount debe ser > 0");

        assertThatThrownBy(() -> baseBuilder()
                .feeAmount(Money.of(new BigDecimal("-1.00"), Currency.USD))
                .build())
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("feeAmount >= 0");
    }

    @Test
    void mark_processing_is_idempotent_for_same_provider_ref() {
        Withdrawal withdrawal = baseBuilder().build();

        withdrawal.markProcessing("po_123");
        withdrawal.markProcessing("po_123");

        assertThat(withdrawal.status()).isEqualTo(Withdrawal.Status.PROCESSING);
        assertThat(withdrawal.providerRef()).isEqualTo("po_123");
    }

    @Test
    void mark_completed_requires_processing_status() {
        Withdrawal withdrawal = baseBuilder().build();

        assertThatThrownBy(withdrawal::markCompleted)
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("No se puede marcar COMPLETED");

        withdrawal.markProcessing("po_123");
        withdrawal.markCompleted();

        assertThat(withdrawal.status()).isEqualTo(Withdrawal.Status.COMPLETED);
        assertThat(withdrawal.processedAt()).isNotNull();
        assertThat(withdrawal.isTerminal()).isTrue();
    }

    @Test
    void mark_failed_is_idempotent_but_rejects_completed_or_cancelled() {
        Withdrawal requested = baseBuilder().build();
        requested.markFailed("provider down");
        requested.markFailed("ignored");

        assertThat(requested.status()).isEqualTo(Withdrawal.Status.FAILED);
        assertThat(requested.failureReason()).isEqualTo("provider down");
        assertThat(requested.isTerminal()).isTrue();

        Withdrawal completed = baseBuilder().build();
        completed.markProcessing("po_999");
        completed.markCompleted();
        assertThatThrownBy(() -> completed.markFailed(null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("no permite marcar FAILED");

        Withdrawal cancelled = baseBuilder().build();
        cancelled.cancel();
        assertThatThrownBy(() -> cancelled.markFailed(null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("no permite marcar FAILED");
    }

    @Test
    void cancel_only_allows_requested_withdrawals() {
        Withdrawal requested = baseBuilder().build();
        requested.cancel();
        requested.cancel();
        assertThat(requested.status()).isEqualTo(Withdrawal.Status.CANCELLED);
        assertThat(requested.isTerminal()).isTrue();

        Withdrawal processing = baseBuilder().build();
        processing.markProcessing("po_456");
        assertThatThrownBy(processing::cancel)
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Solo REQUESTED puede cancelarse");
    }

    private Withdrawal.Builder baseBuilder() {
        return Withdrawal.builder()
                .userId(UserId.generate())
                .walletId(WalletId.generate())
                .payoutDestinationId(PayoutDestinationId.generate())
                .grossAmount(Money.of(new BigDecimal("100.00"), Currency.USD))
                .netAmount(Money.of(new BigDecimal("96.00"), Currency.USD))
                .feeAmount(Money.of(new BigDecimal("4.00"), Currency.USD))
                .createdBy(UUID.randomUUID());
    }
}
