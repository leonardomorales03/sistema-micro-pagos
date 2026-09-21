package com.micropay.backend.api.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class WalletDtos {

    public record CreateWalletRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "COP", allowableValues = {"COP","USD","EUR"})
            @JsonProperty("currency") String currency
    ) {}

    public record WalletResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("user_id") UUID userId,
            @JsonProperty("currency") String currency,
            @JsonProperty("balance") BigDecimal balance,
            @JsonProperty("reserved") BigDecimal reserved,
            @JsonProperty("available") BigDecimal available,
            @JsonProperty("status") String status,
            @JsonProperty("created_by") UUID createdBy,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt,
            @JsonProperty("version") long version
    ) {}
}
