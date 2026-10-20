package com.micropay.backend.api.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ReferralDtos {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReferralResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("code") String code,
            @JsonProperty("referrer_id") UUID referrerId,
            @JsonProperty("invitee_id") UUID inviteeId,
            @Schema(allowableValues = {"PENDING", "QUALIFIED", "EXPIRED"})
            @JsonProperty("status") String status,
            @JsonProperty("referrer_bonus") BigDecimal referrerBonus,
            @JsonProperty("invitee_bonus") BigDecimal inviteeBonus,
            @JsonProperty("bonus_tx_id") UUID bonusTxId,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("qualified_at") Instant qualifiedAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReferralListResponse(
            @JsonProperty("referral_code") String referralCode,
            @JsonProperty("referral_link") String referralLink,
            @JsonProperty("referrals") List<ReferralResponse> referrals
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReferralSummaryResponse(
            @JsonProperty("referral_code") String referralCode,
            @JsonProperty("referral_link") String referralLink,
            @JsonProperty("total_referrals") long totalReferrals,
            @JsonProperty("qualified_count") long qualifiedCount,
            @JsonProperty("pending_count") long pendingCount,
            @JsonProperty("total_bonus_earned") BigDecimal totalBonusEarned
    ) {}
}
