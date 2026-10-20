package com.micropay.backend.api.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs del Backoffice Admin (T24). Todos records; el naming global es SNAKE_CASE.
 */
public final class AdminDtos {

    // ───────────── Requests ─────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AdjustRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "50000", description = "Monto sin signo, > 0")
            @JsonProperty("amount") BigDecimal amount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "COP")
            @JsonProperty("currency") String currency,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "CREDIT o DEBIT", example = "CREDIT")
            @JsonProperty("direction") String direction,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Motivo obligatorio del ajuste")
            @JsonProperty("reason") String reason
    ) {}

    public record ReasonRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Motivo de la acción")
            @JsonProperty("reason") String reason
    ) {}

    public record GrantRolesRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Roles a asignar (SUPPORT, COMPLIANCE, FINANCE, SUPER_ADMIN, ADMIN, USER)")
            @JsonProperty("roles") List<String> roles
    ) {}

    // ───────────── Views ─────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UserSummaryResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("email") String email,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("phone_number") String phoneNumber,
            @JsonProperty("kyc_level") String kycLevel,
            @JsonProperty("status") String status,
            @JsonProperty("email_verified") boolean emailVerified,
            @JsonProperty("roles") List<String> roles,
            @JsonProperty("created_at") Instant createdAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProfileView(
            @JsonProperty("language") String language,
            @JsonProperty("theme") String theme,
            @JsonProperty("preferred_currency") String preferredCurrency,
            @JsonProperty("timezone") String timezone,
            @JsonProperty("address_line") String addressLine,
            @JsonProperty("address_city") String addressCity,
            @JsonProperty("address_country") String addressCountry,
            @JsonProperty("billing_tax_id") String billingTaxId,
            @JsonProperty("profile_picture_url") String profilePictureUrl,
            @JsonProperty("bio") String bio
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WalletSummary(
            @JsonProperty("wallet_id") UUID walletId,
            @JsonProperty("user_id") UUID userId,
            @JsonProperty("currency") String currency,
            @JsonProperty("balance") BigDecimal balance,
            @JsonProperty("reserved") BigDecimal reserved,
            @JsonProperty("status") String status,
            @JsonProperty("created_at") Instant createdAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PayoutDestinationView(
            @JsonProperty("id") UUID id,
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
            @JsonProperty("created_at") Instant createdAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TransactionView(
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
            @JsonProperty("note") String note,
            @JsonProperty("created_by") UUID createdBy,
            @JsonProperty("created_at") Instant createdAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuditEventView(
            @JsonProperty("id") UUID id,
            @JsonProperty("actor_user_id") UUID actorUserId,
            @JsonProperty("action") String action,
            @JsonProperty("entity_type") String entityType,
            @JsonProperty("entity_id") UUID entityId,
            @JsonProperty("metadata") Map<String, Object> metadata,
            @JsonProperty("ip_address") String ipAddress,
            @JsonProperty("user_agent") String userAgent,
            @JsonProperty("created_at") Instant createdAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record KycDocumentView(
            @JsonProperty("document_id") UUID documentId,
            @JsonProperty("user_id") UUID userId,
            @JsonProperty("type") String type,
            @JsonProperty("number") String number,
            @JsonProperty("country") String country,
            @JsonProperty("status") String status,
            @JsonProperty("rejection_reason") String rejectionReason,
            @JsonProperty("submitted_at") Instant submittedAt,
            @JsonProperty("reviewed_at") Instant reviewedAt,
            @JsonProperty("reviewed_by") UUID reviewedBy
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UserDetailResponse(
            @JsonProperty("user") UserSummaryResponse user,
            @JsonProperty("profile") ProfileView profile,
            @JsonProperty("wallets") List<WalletSummary> wallets,
            @JsonProperty("recent_transactions") List<TransactionView> recentTransactions,
            @JsonProperty("payout_destinations") List<PayoutDestinationView> payoutDestinations,
            @JsonProperty("adjustments") List<TransactionView> adjustments,
            @JsonProperty("audit_trail") List<AuditEventView> auditTrail
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WalletDetailResponse(
            @JsonProperty("wallet") WalletSummary wallet,
            @JsonProperty("transactions") List<TransactionView> transactions
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AdjustResponse(
            @JsonProperty("wallet_id") UUID walletId,
            @JsonProperty("currency") String currency,
            @JsonProperty("new_balance") BigDecimal newBalance,
            @JsonProperty("new_reserved") BigDecimal newReserved,
            @JsonProperty("status") String status
    ) {}
}
