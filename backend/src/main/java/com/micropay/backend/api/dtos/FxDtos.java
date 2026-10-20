package com.micropay.backend.api.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class FxDtos {

    public record RateEntry(
            @JsonProperty("base") String base,
            @JsonProperty("quote") String quote,
            @JsonProperty("rate") BigDecimal rate,
            @JsonProperty("source") String source,
            @JsonProperty("fetched_at") Instant fetchedAt
    ) {}

    public record RatesResponse(
            @JsonProperty("base") String base,
            @JsonProperty("rates") List<RateEntry> rates
    ) {}

    public record ConvertRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Wallet UUID origen (moneda de origen)")
            @JsonProperty("wallet_id") UUID walletId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "100000.00", description = "Monto > 0 en la moneda del wallet origen")
            @JsonProperty("amount") BigDecimal amount,

            @Schema(description = "Moneda del monto origen (opcional; por defecto la del wallet origen)", example = "COP")
            @JsonProperty("currency") String currency,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "USD", description = "Moneda destino de la conversión")
            @JsonProperty("target_currency") String targetCurrency
    ) {}

    public record ConvertResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("short_code") String shortCode,
            @JsonProperty("type") String type,
            @JsonProperty("status") String status,
            @JsonProperty("source_currency") String sourceCurrency,
            @JsonProperty("target_currency") String targetCurrency,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("converted_amount") BigDecimal convertedAmount,
            @JsonProperty("fx_rate") BigDecimal fxRate,
            @JsonProperty("fx_source") String fxSource,
            @JsonProperty("created_at") Instant createdAt
    ) {}
}
