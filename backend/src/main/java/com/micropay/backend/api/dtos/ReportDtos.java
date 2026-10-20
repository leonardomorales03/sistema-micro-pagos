package com.micropay.backend.api.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs de Reports, Exports CSV + Reconciliación (T25). Todos records; naming global SNAKE_CASE.
 */
public final class ReportDtos {

    // ───────────── Exports — Requests ─────────────

    public record RequestExportRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "TX_HISTORY | TOPUPS | WITHDRAWALS | ANNUAL_TAX_SUMMARY",
                    example = "TX_HISTORY")
            @JsonProperty("type") String type,
            @Schema(description = "Filtros opcionales (from/to/status/type/currency/year)")
            @JsonProperty("filters") Map<String, Object> filters
    ) {}

    // ───────────── Exports — Views ─────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReportExportResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("requested_by") UUID requestedBy,
            @JsonProperty("type") String type,
            @JsonProperty("filters") Map<String, Object> filters,
            @JsonProperty("status") String status,
            @JsonProperty("file_path") String filePath,
            @JsonProperty("download_token") String downloadToken,
            @JsonProperty("row_count") Long rowCount,
            @JsonProperty("error_message") String errorMessage,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("started_at") Instant startedAt,
            @JsonProperty("completed_at") Instant completedAt,
            @JsonProperty("expires_at") Instant expiresAt
    ) {}

    // ───────────── Cashflow ─────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CashflowResponse(
            @JsonProperty("date") LocalDate date,
            @JsonProperty("credited_by_type") Map<String, BigDecimal> creditedByType,
            @JsonProperty("debited_by_type") Map<String, BigDecimal> debitedByType,
            @JsonProperty("net") BigDecimal net,
            @JsonProperty("fees_by_type") Map<String, BigDecimal> feesByType
    ) {}

    // ───────────── Reconciliation — Requests ─────────────

    public record StartReconciliationRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "stripe")
            @JsonProperty("provider") String provider,
            @Schema(description = "ISO-8601 instant; por defecto inicio del día anterior")
            @JsonProperty("period_start") Instant periodStart,
            @Schema(description = "ISO-8601 instant; por defecto fin del día anterior")
            @JsonProperty("period_end") Instant periodEnd
    ) {}

    public record ResolveMismatchRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "INVESTIGATING | RESOLVED")
            @JsonProperty("status") String status,
            @Schema(description = "Nota obligatoria si status=RESOLVED")
            @JsonProperty("note") String note
    ) {}

    // ───────────── Reconciliation — Views ─────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReconciliationRunResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("triggered_by") UUID triggeredBy,
            @JsonProperty("provider") String provider,
            @JsonProperty("status") String status,
            @JsonProperty("period_start") Instant periodStart,
            @JsonProperty("period_end") Instant periodEnd,
            @JsonProperty("internal_count") int internalCount,
            @JsonProperty("provider_count") int providerCount,
            @JsonProperty("mismatch_count") int mismatchCount,
            @JsonProperty("notes") String notes,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("completed_at") Instant completedAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReconciliationMismatchResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("run_id") UUID runId,
            @JsonProperty("kind") String kind,
            @JsonProperty("internal_tx_id") UUID internalTxId,
            @JsonProperty("provider_ref") String providerRef,
            @JsonProperty("internal_amount") BigDecimal internalAmount,
            @JsonProperty("provider_amount") BigDecimal providerAmount,
            @JsonProperty("currency") String currency,
            @JsonProperty("status") String status,
            @JsonProperty("resolution_note") String resolutionNote,
            @JsonProperty("resolved_by") UUID resolvedBy,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReconciliationDetailResponse(
            @JsonProperty("run") ReconciliationRunResponse run,
            @JsonProperty("mismatches") List<ReconciliationMismatchResponse> mismatches
    ) {}
}
