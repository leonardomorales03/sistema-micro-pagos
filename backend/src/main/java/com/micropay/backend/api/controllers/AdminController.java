package com.micropay.backend.api.controllers;

import com.micropay.backend.api.dtos.AdminDtos;
import com.micropay.backend.domain.entities.AuditEvent;
import com.micropay.backend.domain.entities.PayoutDestination;
import com.micropay.backend.domain.entities.Transaction;
import com.micropay.backend.domain.entities.User;
import com.micropay.backend.domain.entities.UserProfile;
import com.micropay.backend.domain.entities.Wallet;
import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.ports.in.AdminUseCases;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Backoffice Admin (T24). Todos los endpoints requieren autenticación (JWT) y el rol
 * indicado en {@code @PreAuthorize}. Cada mutación propaga actor + ip + user-agent al
 * {@code AuditEvent} (Property 31).
 */
@Tag(name = "Admin", description = "Backoffice: RBAC, ajustes, freeze/unfreeze, cola KYC, consultas")
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private static final String ADMIN_ROLES = "hasAnyRole('SUPPORT','COMPLIANCE','FINANCE','SUPER_ADMIN','ADMIN')";
    private static final String COMPLIANCE_ROLES = "hasAnyRole('COMPLIANCE','SUPER_ADMIN','ADMIN')";
    private static final String FINANCE_ROLES = "hasAnyRole('FINANCE','SUPER_ADMIN','ADMIN')";
    private static final String SUPER_ADMIN_ROLES = "hasAnyRole('SUPER_ADMIN','ADMIN')";

    private final AdminUseCases admin;

    public AdminController(AdminUseCases admin) {
        this.admin = admin;
    }

    // ─────────────────────────── Usuarios ───────────────────────────

    @GetMapping("/users")
    @PreAuthorize(ADMIN_ROLES)
    @Operation(summary = "Buscar usuarios (email/teléfono/nombre/id parcial), paginado",
               security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de usuarios"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Rol insuficiente")
    })
    public ResponseEntity<List<AdminDtos.UserSummaryResponse>> searchUsers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(admin.searchUsers(search, page, pageSize).stream()
                .map(this::toUserSummary).toList());
    }

    @GetMapping("/users/{id}")
    @PreAuthorize(ADMIN_ROLES)
    @Operation(summary = "Detalle completo de usuario (perfil, KYC, wallets, tx, destinos, ajustes, audit trail)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalle de usuario"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    public ResponseEntity<AdminDtos.UserDetailResponse> getUserDetail(@PathVariable UUID id) {
        AdminUseCases.UserDetail d = admin.getUserDetail(id);
        return ResponseEntity.ok(new AdminDtos.UserDetailResponse(
                toUserSummary(d.user()),
                toProfileView(d.profile()),
                d.wallets().stream().map(this::toWalletSummary).toList(),
                d.recentTransactions().stream().map(this::toTxView).toList(),
                d.payoutDestinations().stream().map(this::toPayoutView).toList(),
                d.adjustments().stream().map(this::toTxView).toList(),
                d.auditTrail().stream().map(this::toAuditView).toList()
        ));
    }

    @PostMapping("/users/{id}/roles")
    @PreAuthorize(SUPER_ADMIN_ROLES)
    @Operation(summary = "Asignar roles admin (SUPER_ADMIN/ADMIN)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Roles actualizados"),
            @ApiResponse(responseCode = "403", description = "Rol insuficiente"),
            @ApiResponse(responseCode = "422", description = "Rol no permitido")
    })
    public ResponseEntity<AdminDtos.UserSummaryResponse> grantRoles(
            @PathVariable UUID id,
            @RequestBody AdminDtos.GrantRolesRequest req,
            @Parameter(hidden = true) @CurrentUser UserId adminId,
            HttpServletRequest servlet) {
        User u = admin.grantRoles(id, req.roles(), context(adminId, servlet));
        return ResponseEntity.ok(toUserSummary(u));
    }

    // ─────────────────────────── Wallets ───────────────────────────

    @GetMapping("/wallets/{walletId}")
    @PreAuthorize(ADMIN_ROLES)
    @Operation(summary = "Detalle de wallet + historial de transacciones",
               security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalle de wallet"),
            @ApiResponse(responseCode = "404", description = "Wallet no encontrada")
    })
    public ResponseEntity<AdminDtos.WalletDetailResponse> getWalletDetail(@PathVariable UUID walletId) {
        AdminUseCases.WalletDetail d = admin.getWalletDetail(walletId);
        return ResponseEntity.ok(new AdminDtos.WalletDetailResponse(
                toWalletSummary(d.wallet()),
                d.transactions().stream().map(this::toTxView).toList()
        ));
    }

    @PostMapping("/wallets/{walletId}/adjust")
    @PreAuthorize(FINANCE_ROLES)
    @Operation(summary = "Ajuste manual de saldo (requiere reason)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ajuste aplicado"),
            @ApiResponse(responseCode = "403", description = "Rol insuficiente"),
            @ApiResponse(responseCode = "404", description = "Wallet no encontrada"),
            @ApiResponse(responseCode = "422", description = "reason/amount inválidos")
    })
    public ResponseEntity<AdminDtos.AdjustResponse> adjust(
            @PathVariable UUID walletId,
            @RequestBody AdminDtos.AdjustRequest req,
            @Parameter(hidden = true) @CurrentUser UserId adminId,
            HttpServletRequest servlet) {
        Currency currency = parseCurrency(req.currency());
        Money amount = Money.of(req.amount(), currency);
        boolean isCredit = parseDirection(req.direction());
        Wallet w = admin.adjustWallet(walletId, amount, isCredit, req.reason(), context(adminId, servlet));
        return ResponseEntity.ok(new AdminDtos.AdjustResponse(
                w.id().uuid(),
                w.currency().isoCode(),
                w.balance().amount(),
                w.reserved().amount(),
                w.status().name()
        ));
    }

    @PostMapping("/wallets/{walletId}/freeze")
    @PreAuthorize(COMPLIANCE_ROLES)
    @Operation(summary = "Congelar wallet (UNDER_REVIEW: bloquea débitos, permite créditos)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Wallet congelada"),
            @ApiResponse(responseCode = "404", description = "Wallet no encontrada")
    })
    public ResponseEntity<AdminDtos.WalletSummary> freeze(
            @PathVariable UUID walletId,
            @RequestBody(required = false) AdminDtos.ReasonRequest body,
            @Parameter(hidden = true) @CurrentUser UserId adminId,
            HttpServletRequest servlet) {
        Wallet w = admin.freezeWallet(walletId, body != null ? body.reason() : null, context(adminId, servlet));
        return ResponseEntity.ok(toWalletSummary(w));
    }

    @PostMapping("/wallets/{walletId}/unfreeze")
    @PreAuthorize(COMPLIANCE_ROLES)
    @Operation(summary = "Descongelar wallet (vuelve a ACTIVE)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Wallet descongelada"),
            @ApiResponse(responseCode = "404", description = "Wallet no encontrada")
    })
    public ResponseEntity<AdminDtos.WalletSummary> unfreeze(
            @PathVariable UUID walletId,
            @RequestBody(required = false) AdminDtos.ReasonRequest body,
            @Parameter(hidden = true) @CurrentUser UserId adminId,
            HttpServletRequest servlet) {
        Wallet w = admin.unfreezeWallet(walletId, body != null ? body.reason() : null, context(adminId, servlet));
        return ResponseEntity.ok(toWalletSummary(w));
    }

    // ─────────────────────────── Transacciones ───────────────────────────

    @GetMapping("/transactions")
    @PreAuthorize(ADMIN_ROLES)
    @Operation(summary = "Buscar transacciones con filtros (tipo, estado, wallet, moneda, rango fechas)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<AdminDtos.TransactionView>> listTransactions(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID walletId,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        AdminUseCases.TransactionFilter filter =
                new AdminUseCases.TransactionFilter(walletId, type, status, currency, from, to, page, pageSize);
        return ResponseEntity.ok(admin.listTransactions(filter).stream().map(this::toTxView).toList());
    }

    // ─────────────────────────── KYC queue ───────────────────────────

    @GetMapping("/kyc/queue")
    @PreAuthorize(COMPLIANCE_ROLES)
    @Operation(summary = "Cola KYC pendiente (documentos PENDING)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<AdminDtos.KycDocumentView>> kycQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(admin.listKycQueue(page, pageSize).stream().map(this::toKycView).toList());
    }

    @PostMapping("/kyc/{documentId}/approve")
    @PreAuthorize(COMPLIANCE_ROLES)
    @Operation(summary = "Aprobar documento KYC",
               security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documento aprobado"),
            @ApiResponse(responseCode = "404", description = "Documento no encontrado")
    })
    public ResponseEntity<AdminDtos.KycDocumentView> approveKyc(
            @PathVariable UUID documentId,
            @RequestBody(required = false) AdminDtos.ReasonRequest body,
            @Parameter(hidden = true) @CurrentUser UserId adminId,
            HttpServletRequest servlet) {
        AdminUseCases.KycQueueItem item =
                admin.reviewKyc(documentId, true, body != null ? body.reason() : null, context(adminId, servlet));
        return ResponseEntity.ok(toKycView(item));
    }

    @PostMapping("/kyc/{documentId}/reject")
    @PreAuthorize(COMPLIANCE_ROLES)
    @Operation(summary = "Rechazar documento KYC (reason obligatoria)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documento rechazado"),
            @ApiResponse(responseCode = "404", description = "Documento no encontrado"),
            @ApiResponse(responseCode = "422", description = "reason obligatoria")
    })
    public ResponseEntity<AdminDtos.KycDocumentView> rejectKyc(
            @PathVariable UUID documentId,
            @RequestBody(required = false) AdminDtos.ReasonRequest body,
            @Parameter(hidden = true) @CurrentUser UserId adminId,
            HttpServletRequest servlet) {
        AdminUseCases.KycQueueItem item =
                admin.reviewKyc(documentId, false, body != null ? body.reason() : null, context(adminId, servlet));
        return ResponseEntity.ok(toKycView(item));
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private AdminUseCases.AdminContext context(UserId adminId, HttpServletRequest servlet) {
        String ip = clientIp(servlet);
        String ua = servlet != null ? servlet.getHeader(HttpHeaders.USER_AGENT) : null;
        return new AdminUseCases.AdminContext(adminId != null ? adminId.uuid() : null, ip, ua);
    }

    private static String clientIp(HttpServletRequest req) {
        if (req == null) return null;
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    private Currency parseCurrency(String iso) {
        if (iso == null || iso.isBlank()) {
            throw new BusinessRuleViolationException("CURRENCY_REQUIRED", "currency es requerida");
        }
        try {
            return Currency.valueOf(iso.trim().toUpperCase());
        } catch (Exception e) {
            throw new BusinessRuleViolationException("CURRENCY_INVALID", "Moneda no soportada: " + iso);
        }
    }

    private boolean parseDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            throw new BusinessRuleViolationException("DIRECTION_REQUIRED", "direction es requerida (CREDIT|DEBIT)");
        }
        String d = direction.trim().toUpperCase();
        if ("CREDIT".equals(d)) return true;
        if ("DEBIT".equals(d)) return false;
        throw new BusinessRuleViolationException("DIRECTION_INVALID", "direction debe ser CREDIT o DEBIT");
    }

    private AdminDtos.UserSummaryResponse toUserSummary(User u) {
        return new AdminDtos.UserSummaryResponse(
                u.id().uuid(),
                u.email().value(),
                u.fullName(),
                u.phoneNumber(),
                u.kycLevel().name(),
                u.status().name(),
                u.emailVerified(),
                u.roles(),
                u.createdAt()
        );
    }

    private AdminDtos.ProfileView toProfileView(UserProfile p) {
        if (p == null) return null;
        return new AdminDtos.ProfileView(
                p.language(),
                p.theme() != null ? p.theme().name() : null,
                p.preferredCurrency(),
                p.timezone(),
                p.addressLine(),
                p.addressCity(),
                p.addressCountry(),
                p.billingTaxId(),
                p.profilePictureUrl(),
                p.bio()
        );
    }

    private AdminDtos.WalletSummary toWalletSummary(Wallet w) {
        return new AdminDtos.WalletSummary(
                w.id().uuid(),
                w.userId().uuid(),
                w.currency().isoCode(),
                w.balance().amount(),
                w.reserved().amount(),
                w.status().name(),
                w.createdAt()
        );
    }

    private AdminDtos.TransactionView toTxView(Transaction t) {
        return new AdminDtos.TransactionView(
                t.id().uuid(),
                t.type().name(),
                t.shortCode() != null ? t.shortCode().value() : null,
                t.walletFromId() != null ? t.walletFromId().uuid() : null,
                t.walletToId() != null ? t.walletToId().uuid() : null,
                t.grossAmount().amount(),
                t.netAmount().amount(),
                t.feeAmount().amount(),
                t.grossAmount().currency().isoCode(),
                t.status().name(),
                t.note(),
                t.createdBy(),
                t.createdAt()
        );
    }

    private AdminDtos.PayoutDestinationView toPayoutView(PayoutDestination d) {
        return new AdminDtos.PayoutDestinationView(
                d.id().uuid(),
                d.type() != null ? d.type().name() : null,
                d.maskedLabel(),
                d.bankName(),
                d.accountType(),
                d.accountNumberMasked(),
                d.last4(),
                d.holderName(),
                d.country(),
                d.currency(),
                d.isDefault(),
                d.verified(),
                d.createdAt()
        );
    }

    private AdminDtos.AuditEventView toAuditView(AuditEvent a) {
        return new AdminDtos.AuditEventView(
                a.id(),
                a.actorUserId(),
                a.action(),
                a.entityType(),
                a.entityId(),
                a.metadata(),
                a.ipAddress(),
                a.userAgent(),
                a.createdAt()
        );
    }

    private AdminDtos.KycDocumentView toKycView(AdminUseCases.KycQueueItem i) {
        return new AdminDtos.KycDocumentView(
                i.documentId(),
                i.userId(),
                i.type(),
                i.number(),
                i.country(),
                i.status(),
                i.rejectionReason(),
                i.submittedAt(),
                i.reviewedAt(),
                i.reviewedBy()
        );
    }
}
