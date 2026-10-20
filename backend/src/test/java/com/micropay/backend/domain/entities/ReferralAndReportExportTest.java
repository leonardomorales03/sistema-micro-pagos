package com.micropay.backend.domain.entities;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReferralAndReportExportTest {

    @Test
    void referral_transitions_from_pending_to_qualified() {
        UUID referrerId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        Referral referral = Referral.pending("LEO123", referrerId, inviteeId);
        Instant qualifiedAt = Instant.parse("2026-10-20T10:15:30Z");
        UUID bonusTxId = UUID.randomUUID();

        Referral qualified = referral.qualified(new BigDecimal("5.00"), bonusTxId, qualifiedAt);

        assertThat(referral.status()).isEqualTo(Referral.ReferralStatus.PENDING);
        assertThat(qualified.status()).isEqualTo(Referral.ReferralStatus.QUALIFIED);
        assertThat(qualified.referrerBonus()).isEqualByComparingTo("5.00");
        assertThat(qualified.bonusTxId()).isEqualTo(bonusTxId);
        assertThat(qualified.qualifiedAt()).isEqualTo(qualifiedAt);
    }

    @Test
    void report_export_transitions_preserve_job_identity() {
        UUID requestedBy = UUID.randomUUID();
        ReportExport queued = ReportExport.queued(requestedBy, "TRANSACTIONS", Map.of("wallet_id", "w1"));
        Instant startedAt = Instant.parse("2026-10-20T11:00:00Z");

        ReportExport running = queued.running(startedAt);
        ReportExport completed = running.completed("/tmp/report.csv", "token-123", 42L,
                Instant.parse("2026-10-21T11:00:00Z"));
        ReportExport failed = running.failed("disk full");

        assertThat(queued.status()).isEqualTo(ReportExport.QUEUED);
        assertThat(running.id()).isEqualTo(queued.id());
        assertThat(running.status()).isEqualTo(ReportExport.RUNNING);
        assertThat(running.startedAt()).isEqualTo(startedAt);

        assertThat(completed.status()).isEqualTo(ReportExport.COMPLETED);
        assertThat(completed.filePath()).isEqualTo("/tmp/report.csv");
        assertThat(completed.downloadToken()).isEqualTo("token-123");
        assertThat(completed.rowCount()).isEqualTo(42L);

        assertThat(failed.status()).isEqualTo(ReportExport.FAILED);
        assertThat(failed.errorMessage()).isEqualTo("disk full");
    }
}
