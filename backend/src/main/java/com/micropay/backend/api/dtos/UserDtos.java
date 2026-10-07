package com.micropay.backend.api.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class UserDtos {

    public record RegisterRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "nuevo@ejemplo.com")
            @JsonProperty("email") String email,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Password en texto plano (se encripta bcrypt cost=12 server-side sobre HTTPS)")
            @JsonProperty("password") String password,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "María López")
            @JsonProperty("full_name") String fullName,
            @Schema(description = "Código referido (8 chars uppercase) — opcional")
            @JsonProperty("referral_code") String referralCode
    ) {}

    public record BlockRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Comportamiento sospechoso detectado")
            @JsonProperty("reason") String reason
    ) {}

    public record VerifyEmailTokenRequest(
            @Schema(description = "JWT firmado corto con purpose VERIFY_EMAIL", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("token") String token
    ) {}

    public record ProfilePreferencesRequest(
            @Schema(example = "es_CO", allowableValues = {"es_CO","es_ES","en_US","pt_BR"})
            @JsonProperty("language") String language,
            @Schema(allowableValues = {"LIGHT","DARK","SYSTEM"})
            @JsonProperty("theme") String theme,
            @Schema(example = "USD")
            @JsonProperty("preferred_currency") String preferredCurrency,
            @JsonProperty("timezone") String timezone,
            @JsonProperty("push_enabled") boolean pushEnabled,
            @JsonProperty("email_notif_enabled") boolean emailNotifEnabled,
            @JsonProperty("marketing_opt_in") boolean marketingOptIn
    ) {}

    public record ProfileContactRequest(
            @JsonProperty("address_line") String addressLine,
            @JsonProperty("address_city") String addressCity,
            @JsonProperty("address_country_iso2") String addressCountry,
            @JsonProperty("billing_tax_id") String billingTaxId,
            @JsonProperty("profile_picture_url") String profilePictureUrl,
            @JsonProperty("bio") String bio
    ) {}

    public record KycSubmitRequest(
            @Schema(description = "Tipo documento: CC, CE, NIT, PASSPORT, OTHER", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("document_type") String documentType,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("document_number") String documentNumber,
            @Schema(description = "ISO2 país emisor documento", example = "CO", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("country_iso2") String countryIso2,
            @JsonProperty("payload_base64") String payloadBase64
    ) {}

    public record KycUpgradeAdminRequest(
            @Schema(allowableValues = {"LEVEL_0","LEVEL_1","LEVEL_2"}, requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("target_kyc_level") String targetKycLevel
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UserResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("email") String email,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("phone_number") String phoneNumber,
            @JsonProperty("kyc_level") String kycLevel,
            @JsonProperty("status") String status,
            @JsonProperty("email_verified") boolean emailVerified,
            @JsonProperty("referral_code") String referralCode,
            @JsonProperty("referred_by") UUID referredBy,
            @JsonProperty("roles") List<String> roles,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UserProfileResponse(
            @JsonProperty("user_id") UUID userId,
            @JsonProperty("language") String language,
            @JsonProperty("theme") String theme,
            @JsonProperty("preferred_currency") String preferredCurrency,
            @JsonProperty("timezone") String timezone,
            @JsonProperty("push_enabled") boolean pushEnabled,
            @JsonProperty("email_notif_enabled") boolean emailNotifEnabled,
            @JsonProperty("marketing_opt_in") boolean marketingOptIn,
            @JsonProperty("address_line") String addressLine,
            @JsonProperty("address_city") String addressCity,
            @JsonProperty("address_country_iso2") String addressCountry,
            @JsonProperty("billing_tax_id") String billingTaxId,
            @JsonProperty("profile_picture_url") String profilePictureUrl,
            @JsonProperty("bio") String bio,
            @JsonProperty("referral_bonus_claimed") boolean referralBonusClaimed,
            @JsonProperty("tier_limit") TierLimitView tierLimit,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TierLimitView(
            @JsonProperty("kyc_level") String kycLevel,
            @JsonProperty("country_iso2") String countryIso2,
            @JsonProperty("currency") String currency,
            @JsonProperty("max_single_tx") BigDecimal maxSingleTx,
            @JsonProperty("max_daily_volume") BigDecimal maxDailyVolume,
            @JsonProperty("max_monthly_volume") BigDecimal maxMonthlyVolume,
            @JsonProperty("max_wallet_balance") BigDecimal maxWalletBalance,
            @JsonProperty("max_topups_per_day") int maxTopupsPerDay,
            @JsonProperty("max_withdrawals_per_day") int maxWithdrawalsPerDay,
            @JsonProperty("withdrawal_fee_bps") BigDecimal withdrawalFeeBps
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record KycSubmissionResponse(
            @JsonProperty("provider_ref") String providerRef,
            @JsonProperty("document_type") String documentType,
            @JsonProperty("document_number") String documentNumber,
            @JsonProperty("country_iso2") String countryIso2,
            @JsonProperty("status") String status,
            @JsonProperty("achieved_kyc_level") String achievedKycLevel,
            @JsonProperty("rejection_reason") String rejectionReason,
            @JsonProperty("reviewed_at") Instant reviewedAt
    ) {}
}
