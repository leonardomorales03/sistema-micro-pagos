package com.micropay.backend.api.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PayoutDestinationDtos {

    public record CreatePayoutDestinationRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Número de cuenta (se enmascara, no se persiste en claro)")
            @JsonProperty("account_number") String accountNumber,

            @Schema(example = "Bancolombia") @JsonProperty("bank_name") String bankName,
            @Schema(example = "SAVINGS", allowableValues = {"SAVINGS","CHECKING"}) @JsonProperty("account_type") String accountType,
            @Schema(example = "Juan Pérez") @JsonProperty("holder_name") String holderName,
            @Schema(example = "CO") @JsonProperty("country") String country,
            @Schema(example = "COP") @JsonProperty("currency") String currency,
            @JsonProperty("is_default") Boolean isDefault
    ) {}

    public record PayoutDestinationResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("user_id") UUID userId,
            @JsonProperty("type") String type,
            @JsonProperty("masked_label") String maskedLabel,
            @JsonProperty("bank_name") String bankName,
            @JsonProperty("account_type") String accountType,
            @JsonProperty("account_number_masked") String accountNumberMasked,
            @JsonProperty("last4") String last4,
            @JsonProperty("holder_name") String holderName,
            @JsonProperty("country") String country,
            @JsonProperty("currency") String currency,
            @JsonProperty("is_default") boolean isDefault,
            @JsonProperty("verified") boolean verified,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt
    ) {}

    public record PayoutDestinationListResponse(
            @JsonProperty("data") List<PayoutDestinationResponse> data
    ) {}
}
