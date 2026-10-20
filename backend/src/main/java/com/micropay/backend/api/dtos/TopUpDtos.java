package com.micropay.backend.api.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class TopUpDtos {

    public record CreateTopUpRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Wallet UUID destino")
            @JsonProperty("wallet_id") UUID walletId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "50000.00", description = "Monto > 0")
            @JsonProperty("amount") BigDecimal amount,

            @Schema(description = "Moneda del topup (opcional; por defecto COP). Otras monedas requieren flag multi-moneda ON.",
                    example = "COP", allowableValues = {"COP","USD","EUR","ARS","BRL","JPY"})
            @JsonProperty("currency") String currency,

            @Schema(example = "https://app.ejemplo.com/topups/success")
            @JsonProperty("success_url") String successUrl,

            @Schema(example = "https://app.ejemplo.com/topups/cancel")
            @JsonProperty("cancel_url") String cancelUrl,

            @JsonProperty("idempotency_key") String idempotencyKey
    ) {}

    public record TopUpResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("user_id") UUID userId,
            @JsonProperty("wallet_id") UUID walletId,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("net_amount") BigDecimal netAmount,
            @JsonProperty("fee_amount") BigDecimal feeAmount,
            @JsonProperty("currency") String currency,
            @JsonProperty("provider") String provider,
            @JsonProperty("provider_ref") String providerRef,
            @JsonProperty("checkout_url") String checkoutUrl,
            @JsonProperty("status") String status,
            @JsonProperty("failure_reason") String failureReason,
            @JsonProperty("paid_at") Instant paidAt,
            @JsonProperty("transaction_ref") UUID transactionRef,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt
    ) {}

    public record TopUpListResponse(
            @JsonProperty("data") java.util.List<TopUpResponse> data,
            @JsonProperty("page") int page,
            @JsonProperty("page_size") int pageSize
    ) {}
}
