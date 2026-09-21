package com.micropay.backend.api.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TransactionDtos {

    public record TransferRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Wallet ID origen (UUID)")
            @JsonProperty("wallet_from_id") UUID walletFromId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Wallet ID destino (UUID)")
            @JsonProperty("wallet_to_id") UUID walletToId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "50000", description = "Monto sin signo, > 0")
            @JsonProperty("amount") BigDecimal amount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "COP")
            @JsonProperty("currency") String currency,
            @Schema(description = "Nota opcional (max 255 chars)", example = "Pago almuerzo")
            @JsonProperty("note") String note
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TransactionResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("type") String type,
            @JsonProperty("short_code") String shortCode,
            @JsonProperty("wallet_from_id") UUID walletFromId,
            @JsonProperty("wallet_to_id") UUID walletToId,
            @JsonProperty("gross_amount") BigDecimal grossAmount,
            @JsonProperty("net_amount") BigDecimal netAmount,
            @JsonProperty("fee_amount") BigDecimal feeAmount,
            @JsonProperty("currency") String currency,
            @JsonProperty("status") String status,
            @JsonProperty("external_ref") String externalRef,
            @JsonProperty("note") String note,
            @JsonProperty("failure_reason") String failureReason,
            @JsonProperty("created_by") UUID createdBy,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt,
            @JsonProperty("account_moves") List<AccountMoveResponse> accountMoves
    ) {}

    public record AccountMoveResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("wallet_id") UUID walletId,
            @JsonProperty("transaction_id") UUID transactionId,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("balance_after") BigDecimal balanceAfter,
            @JsonProperty("created_at") Instant createdAt
    ) {}
}
