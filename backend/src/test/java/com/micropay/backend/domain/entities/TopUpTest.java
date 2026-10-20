package com.micropay.backend.domain.entities;

import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.TopUpId;
import com.micropay.backend.domain.valueobjects.TransactionId;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TopUpTest {

    @Test
    void build_applies_defaults_and_copies_metadata() {
        Instant createdAt = Instant.parse("2026-10-20T10:15:30Z");
        TopUp topUp = baseBuilder()
                .metadata(Map.of("provider", "stripe"))
                .createdAt(createdAt)
                .build();

        assertThat(topUp.id()).isInstanceOf(TopUpId.class);
        assertThat(topUp.provider()).isEqualTo("STRIPE_MOCK");
        assertThat(topUp.status()).isEqualTo(TopUp.Status.PENDING_PAYMENT);
        assertThat(topUp.createdAt()).isEqualTo(createdAt);
        assertThat(topUp.updatedAt()).isEqualTo(createdAt);
        assertThat(topUp.metadata()).containsEntry("provider", "stripe");
    }

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
    void mark_paid_is_idempotent_for_same_transaction_and_rejects_mismatch() {
        TransactionId firstTx = TransactionId.generate();
        TopUp topUp = baseBuilder().build();

        topUp.markPaid(firstTx);
        Instant paidAt = topUp.paidAt();

        topUp.markPaid(firstTx);

        assertThat(topUp.status()).isEqualTo(TopUp.Status.PAID);
        assertThat(topUp.transactionRef()).isEqualTo(firstTx);
        assertThat(topUp.paidAt()).isEqualTo(paidAt);

        assertThatThrownBy(() -> topUp.markPaid(TransactionId.generate()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("ya está PAID");
    }

    @Test
    void mark_paid_rejects_non_pending_status() {
        TopUp topUp = baseBuilder().status(TopUp.Status.FAILED).build();

        assertThatThrownBy(() -> topUp.markPaid(TransactionId.generate()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("No se puede marcar PAID");
    }

    @Test
    void mark_failed_behaves_as_expected_for_pending_terminal_and_paid_states() {
        TopUp pending = baseBuilder().build();
        pending.markFailed("provider timeout");
        assertThat(pending.status()).isEqualTo(TopUp.Status.FAILED);
        assertThat(pending.failureReason()).isEqualTo("provider timeout");

        pending.markFailed("ignored");
        assertThat(pending.failureReason()).isEqualTo("provider timeout");

        TopUp cancelled = baseBuilder().status(TopUp.Status.CANCELLED).build();
        cancelled.markFailed("ignored");
        assertThat(cancelled.status()).isEqualTo(TopUp.Status.CANCELLED);

        TopUp paid = baseBuilder().build();
        paid.markPaid(TransactionId.generate());
        assertThatThrownBy(() -> paid.markFailed(null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("no permite marcar FAILED");
    }

    @Test
    void set_checkout_url_only_updates_pending_topups() {
        TopUp pending = baseBuilder().build();
        pending.setCheckoutUrl("https://checkout.example/pending");
        assertThat(pending.checkoutUrl()).isEqualTo("https://checkout.example/pending");

        TopUp paid = baseBuilder().build();
        paid.markPaid(TransactionId.generate());
        paid.setCheckoutUrl("https://checkout.example/ignored");
        assertThat(paid.checkoutUrl()).isNull();
    }

    private TopUp.Builder baseBuilder() {
        return TopUp.builder()
                .userId(UserId.generate())
                .walletId(WalletId.generate())
                .grossAmount(Money.of(new BigDecimal("100.00"), Currency.USD))
                .netAmount(Money.of(new BigDecimal("97.00"), Currency.USD))
                .feeAmount(Money.of(new BigDecimal("3.00"), Currency.USD))
                .providerRef("prov-" + UUID.randomUUID())
                .createdBy(UUID.randomUUID());
    }
}
