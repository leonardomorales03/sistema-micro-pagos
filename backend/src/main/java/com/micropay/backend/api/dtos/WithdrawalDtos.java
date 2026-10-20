package com.micropay.backend.api.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class WithdrawalDtos {

    public record CreateWithdrawalRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Wallet UUID origen de los fondos")
            @JsonProperty("wallet_id") UUID walletId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "PayoutDestination UUID destino")
            @JsonProperty("payout_destination_id") UUID payoutDestinationId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "100000.00", description = "Monto > 0")
            @JsonProperty("amount") BigDecimal amount,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "COP", allowableValues = {"COP","USD","EUR"})
            @JsonProperty("currency") String currency,

            @JsonProperty("idempotency_key") String idempotencyKey
    ) {}

    public record WithdrawalResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("user_id") UUID userId,
            @JsonProperty("wallet_id") UUID walletId,
            @JsonProperty("payout_destination_id") UUID payoutDestinationId,
            @JsonProperty("gross_amount") BigDecimal grossAmount,
            @JsonProperty("net_amount") BigDecimal netAmount,
            @JsonProperty("fee_amount") BigDecimal feeAmount,
            @JsonProperty("currency") String currency,
            @JsonProperty("provider") String provider,
            @JsonProperty("provider_ref") String providerRef,
            @JsonProperty("status") String status,
            @JsonProperty("failure_reason") String failureReason,
            @JsonProperty("note") String note,
            @JsonProperty("requested_at") Instant requestedAt,
            @JsonProperty("processed_at") Instant processedAt,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt
    ) {}

    public record WithdrawalListResponse(
            @JsonProperty("data") List<WithdrawalResponse> data,
            @JsonProperty("page") int page,
            @JsonProperty("page_size") int pageSize
    ) {}
}
