package com.micropay.backend.api.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PaymentRequestDtos {

    /** Payload de creación (comercio). */
    public record CreatePaymentRequestRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Wallet del comercio que recibe el pago")
            @JsonProperty("wallet_id") UUID walletId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "25000.00", description = "Monto > 0")
            @JsonProperty("amount") BigDecimal amount,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "COP", allowableValues = {"COP","USD","EUR"})
            @JsonProperty("currency") String currency,

            @Schema(description = "Descripción del cobro (máx 255)")
            @JsonProperty("description") String description,

            @Schema(description = "Referencia de orden del comercio")
            @JsonProperty("order_reference") String orderReference,

            @Schema(description = "Expiración ISO-8601 (por defecto: +24h)")
            @JsonProperty("expires_at") Instant expiresAt,

            @Schema(description = "URL del backend del comercio a notificar (opcional)")
            @JsonProperty("webhook_url") String webhookUrl
    ) {}

    /** Vista completa del comercio (incluye link/QR). */
    public record PaymentRequestResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("merchant_user_id") UUID merchantUserId,
            @JsonProperty("merchant_wallet_id") UUID merchantWalletId,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency") String currency,
            @JsonProperty("description") String description,
            @JsonProperty("order_reference") String orderReference,
            @JsonProperty("short_code") String shortCode,
            @JsonProperty("short_url") String shortUrl,
            @JsonProperty("qr_payload") String qrPayload,
            @JsonProperty("status") String status,
            @JsonProperty("paid_with_tx_id") UUID paidWithTxId,
            @JsonProperty("expires_at") Instant expiresAt,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("paid_at") Instant paidAt
    ) {}

    /** Vista pública mínima (cliente abre el link/QR) — sin datos del comercio. */
    public record PublicPaymentRequestResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("short_code") String shortCode,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency") String currency,
            @JsonProperty("description") String description,
            @JsonProperty("status") String status,
            @JsonProperty("expires_at") Instant expiresAt,
            @JsonProperty("created_at") Instant createdAt
    ) {}

    /** Recibo del pago (Requisito 21.4/21.5). */
    public record PayPaymentRequestResponse(
            @JsonProperty("payment_request_id") UUID paymentRequestId,
            @JsonProperty("short_code") String shortCode,
            @JsonProperty("status") String status,
            @JsonProperty("transaction_id") UUID transactionId,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency") String currency,
            @JsonProperty("idempotent_replay") boolean idempotentReplay
    ) {}

    public record PaymentRequestListResponse(
            @JsonProperty("data") List<PaymentRequestResponse> data,
            @JsonProperty("page") int page,
            @JsonProperty("page_size") int pageSize
    ) {}
}
