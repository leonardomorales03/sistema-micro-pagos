package com.micropay.backend.api.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTOs de Reembolsos (Requirement 23).
 */
public final class RefundDtos {

    @Schema(name = "CreateRefundRequest", description = "Solicitud de reembolso (total o parcial) sobre una transacción COMPLETED")
    public record CreateRefundRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "50000", description = "Monto a reembolsar, > 0 y ≤ reembolsable restante")
            @JsonProperty("refunded_amount") BigDecimal refundedAmount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "COP", description = "Debe coincidir con la moneda original")
            @JsonProperty("currency") String currency,
            @Schema(description = "Motivo del reembolso (opcional)", example = "Producto no entregado")
            @JsonProperty("reason") String reason
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "RefundResponse", description = "Reembolso registrado")
    public record RefundResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("source_transaction_id") UUID sourceTransactionId,
            @JsonProperty("refunding_user_id") UUID refundingUserId,
            @JsonProperty("refunded_amount") BigDecimal refundedAmount,
            @JsonProperty("currency") String currency,
            @JsonProperty("type") String type,
            @JsonProperty("reason") String reason,
            @JsonProperty("refund_tx_id") UUID refundTxId,
            @JsonProperty("created_at") Instant createdAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "RefundListResponse", description = "Historial de reembolsos de una transacción")
    public record RefundListResponse(
            @JsonProperty("items") java.util.List<RefundResponse> items,
            @JsonProperty("total") int total
    ) {}
}
