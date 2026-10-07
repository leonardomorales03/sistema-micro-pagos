package com.micropay.backend.api.controllers;

import com.micropay.backend.api.dtos.UserDtos;
import com.micropay.backend.application.services.UserService;
import com.micropay.backend.domain.entities.User;
import com.micropay.backend.domain.entities.UserProfile;
import com.micropay.backend.domain.exceptions.UserNotFoundException;
import com.micropay.backend.domain.ports.in.UserUseCases;
import com.micropay.backend.domain.ports.out.KycProviderPort;
import com.micropay.backend.domain.ports.out.TierLimitsPort;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.DocumentNumber;
import com.micropay.backend.domain.valueobjects.Email;
import com.micropay.backend.domain.valueobjects.KycLevel;
import com.micropay.backend.domain.valueobjects.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Tag(name = "Users", description = "Usuarios: registrar, perfil, KYC, verificar email, bloquear (admin), listar")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserUseCases users;
    private final UserService userService;

    public UserController(UserService users) {
        this.users = users;
        this.userService = users;
    }

    @PostMapping("/register")
    @Operation(summary = "Registrar un usuario nuevo",
               description = "Endpoint público. Crea User en estado PENDING_EMAIL_VERIFICATION + user_profile. Emite notificación WELCOME_VERIFY_EMAIL con verify_token JWT corto (10 min).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario creado exitosamente"),
            @ApiResponse(responseCode = "422", description = "Email inválido / password demasiado corto / email ya existe")
    })
    public ResponseEntity<UserDtos.UserResponse> register(@RequestBody UserDtos.RegisterRequest req) {
        UserId referred = null;
        if (req.referralCode() != null && !req.referralCode().isBlank()) {
            User r = users.findByReferralCode(req.referralCode()).orElse(null);
            if (r != null) referred = r.id();
        }
        User created = users.register(new Email(req.email()), req.password(), req.fullName(), referred);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResp(created));
    }

    @GetMapping("/me")
    @Operation(summary = "Obtener datos básicos usuario autenticado", security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil encontrado"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    public ResponseEntity<UserDtos.UserResponse> me(@Parameter(hidden = true) @CurrentUser UserId currentId) {
        User u = users.findById(currentId)
                .orElseThrow(() -> new UserNotFoundException(currentId.uuid()));
        return ResponseEntity.ok(toResp(u));
    }

    @GetMapping("/me/profile")
    @Operation(summary = "Obtener perfil extendido (preferencias + contacto + tier limit)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil extendido con límites KYC/tier"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    public ResponseEntity<UserDtos.UserProfileResponse> meProfile(
            @Parameter(hidden = true) @CurrentUser UserId currentId) {
        User u = users.findById(currentId)
                .orElseThrow(() -> new UserNotFoundException(currentId.uuid()));
        UserProfile p = userService.getProfileOrCreate(currentId);
        String country = p.addressCountry() != null ? p.addressCountry() : "CO";
        Currency preferred;
        try {
            preferred = Currency.valueOf(p.preferredCurrency());
        } catch (Exception e) {
            preferred = Currency.valueOf("USD");
        }
        TierLimitsPort.TierLimit tl = userService.getCurrentTierLimit(currentId, country, preferred);
        return ResponseEntity.ok(toProfileResp(u, p, tl));
    }

    @PutMapping("/me/profile")
    @Operation(summary = "Actualizar preferencias perfil (language/theme/currency/timezone/notifications)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preferencias actualizadas"),
            @ApiResponse(responseCode = "422", description = "Valores inválidos (idioma, theme, currency)"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    public ResponseEntity<UserDtos.UserProfileResponse> updatePreferences(
            @RequestBody UserDtos.ProfilePreferencesRequest req,
            @Parameter(hidden = true) @CurrentUser UserId currentId) {
        UserProfile.Theme theme;
        try {
            theme = req.theme() == null ? UserProfile.Theme.SYSTEM
                    : UserProfile.Theme.valueOf(req.theme().toUpperCase());
        } catch (Exception e) {
            throw new com.micropay.backend.domain.exceptions.BusinessRuleViolationException(
                    "INVALID_THEME", "Theme inválido: " + req.theme());
        }
        UserProfile p = userService.updatePreferences(currentId,
                req.language(), theme,
                req.preferredCurrency(), req.timezone(),
                req.pushEnabled(), req.emailNotifEnabled(), req.marketingOptIn());
        User u = users.findById(currentId).orElseThrow();
        Currency preferred;
        try { preferred = Currency.valueOf(p.preferredCurrency()); } catch (Exception e) { preferred = Currency.valueOf("USD"); }
        TierLimitsPort.TierLimit tl = userService.getCurrentTierLimit(currentId,
                p.addressCountry() != null ? p.addressCountry() : "CO", preferred);
        return ResponseEntity.ok(toProfileResp(u, p, tl));
    }

    @PutMapping("/me/profile/contact")
    @Operation(summary = "Actualizar datos contacto perfil (dirección, ciudad, país ISO2, taxId, foto, bio)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Datos contacto actualizados"),
            @ApiResponse(responseCode = "422", description = "País ISO2 inválido / bio >200 chars"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    public ResponseEntity<UserDtos.UserProfileResponse> updateContact(
            @RequestBody UserDtos.ProfileContactRequest req,
            @Parameter(hidden = true) @CurrentUser UserId currentId) {
        UserProfile p = userService.updateContactAddress(currentId,
                req.addressLine(), req.addressCity(), req.addressCountry(),
                req.billingTaxId(), req.profilePictureUrl(), req.bio());
        User u = users.findById(currentId).orElseThrow();
        Currency preferred;
        try { preferred = Currency.valueOf(p.preferredCurrency()); } catch (Exception e) { preferred = Currency.valueOf("USD"); }
        TierLimitsPort.TierLimit tl = userService.getCurrentTierLimit(currentId,
                p.addressCountry() != null ? p.addressCountry() : "CO", preferred);
        return ResponseEntity.ok(toProfileResp(u, p, tl));
    }

    @PostMapping("/me/kyc")
    @Operation(summary = "Enviar documento KYC para validación (sandbox auto-aprueba: 1er doc→LEVEL_1, 2do→LEVEL_2)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Documento KYC enviado y procesado (sandbox: approved instantáneo)"),
            @ApiResponse(responseCode = "422", description = "Documento/tipo/payload inválido"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    public ResponseEntity<UserDtos.KycSubmissionResponse> submitKyc(
            @RequestBody UserDtos.KycSubmitRequest req,
            @Parameter(hidden = true) @CurrentUser UserId currentId) {
        byte[] payload = null;
        if (req.payloadBase64() != null && !req.payloadBase64().isBlank()) {
            try {
                payload = Base64.getDecoder().decode(req.payloadBase64());
            } catch (Exception e) {
                throw new com.micropay.backend.domain.exceptions.BusinessRuleViolationException(
                        "INVALID_PAYLOAD_BASE64", "payload_base64 no es Base64 válido");
            }
        }
        DocumentNumber doc;
        try {
            doc = new DocumentNumber(req.documentType(), req.documentNumber(), req.countryIso2());
        } catch (Exception e) {
            throw new com.micropay.backend.domain.exceptions.BusinessRuleViolationException(
                    "INVALID_DOCUMENT", "Documento inválido: " + e.getMessage());
        }
        KycProviderPort.KycSubmissionResult r = userService.submitKycDocument(currentId, doc, payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(toKycResp(r));
    }

    @GetMapping("/me/kyc")
    @Operation(summary = "Listar envíos KYC del usuario autenticado",
               security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<UserDtos.KycSubmissionResponse>> listKycMe(
            @Parameter(hidden = true) @CurrentUser UserId currentId) {
        return ResponseEntity.ok(userService.listKyc(currentId).stream().map(this::toKycResp).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN') or #id == authentication.principal.userId")
    @Operation(summary = "Buscar usuario por ID (ROLE_USER propio, ROLE_ADMIN cualquiera)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<UserDtos.UserResponse> findById(@PathVariable UUID id) {
        User u = users.findById(UserId.from(id))
                .orElseThrow(() -> new UserNotFoundException(id));
        return ResponseEntity.ok(toResp(u));
    }

    @GetMapping(params = {"email"})
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Buscar usuario por email (sólo ADMIN)", security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<UserDtos.UserResponse> findByEmail(@RequestParam String email) {
        User u = users.findByEmail(new Email(email))
                .orElseThrow(() -> new UserNotFoundException("email=" + email));
        return ResponseEntity.ok(toResp(u));
    }

    @GetMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Listar usuarios (sólo ADMIN, paginado)", security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<UserDtos.UserResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(users.listUsers(page, pageSize).stream().map(this::toResp).toList());
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verificar email mediante token JWT firmado (purpose VERIFY_EMAIL, TTL 10 min)",
               description = "Endpoint público. Este es el método preferido; el UUID público /{id}/verify-email está deprecado por seguridad.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Email verificado correctamente"),
            @ApiResponse(responseCode = "422", description = "Token inválido, expirado o con propósito incorrecto")
    })
    public ResponseEntity<Void> verifyEmailByToken(@RequestBody UserDtos.VerifyEmailTokenRequest req) {
        if (req.token() == null || req.token().isBlank()) {
            throw new com.micropay.backend.domain.exceptions.BusinessRuleViolationException(
                    "VERIFY_TOKEN_REQUIRED", "Falta token de verificación");
        }
        java.util.Optional<UserId> ok = userService.verifyEmailByToken(req.token());
        if (ok.isEmpty()) {
            throw new com.micropay.backend.domain.exceptions.BusinessRuleViolationException(
                    "VERIFY_TOKEN_INVALID", "Token de verificación inválido o expirado");
        }
        return ResponseEntity.noContent().build();
    }

    @Deprecated
    @PostMapping("/{id}/verify-email")
    @Operation(summary = "[DEPRECATED] Marcar email verificado vía UUID público (link legacy). Usar /verify-email con JWT token.",
               deprecated = true)
    public ResponseEntity<Void> verifyEmail(@PathVariable UUID id) {
        users.verifyEmail(UserId.from(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/upgrade-kyc")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Forzar upgrade nivel KYC manualmente (sólo ADMIN)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Upgrade aplicado"),
            @ApiResponse(responseCode = "403", description = "No es ADMIN"),
            @ApiResponse(responseCode = "422", description = "target_kyc_level inválido (LEVEL_0/LEVEL_1/LEVEL_2)")
    })
    public ResponseEntity<Void> upgradeKycByAdmin(@PathVariable UUID id,
                                                  @RequestBody UserDtos.KycUpgradeAdminRequest req,
                                                  @Parameter(hidden = true) @CurrentUser UserId adminId) {
        KycLevel target;
        try {
            target = KycLevel.valueOf(req.targetKycLevel().toUpperCase());
        } catch (Exception e) {
            throw new com.micropay.backend.domain.exceptions.BusinessRuleViolationException(
                    "INVALID_KYC_LEVEL", "target_kyc_level inválido: " + req.targetKycLevel());
        }
        userService.upgradeKycLevelByAdmin(UserId.from(id), target, adminId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/block")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Bloquear usuario (sólo ADMIN)", security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> block(@PathVariable UUID id,
                                      @RequestBody(required = false) UserDtos.BlockRequest body,
                                      @Parameter(hidden = true) @CurrentUser UserId adminId) {
        users.blockUser(UserId.from(id), body != null ? body.reason() : "ADMIN", adminId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unblock")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Desbloquear usuario (sólo ADMIN)", security = @SecurityRequirement(name = "bearerAuth"))
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> unblock(@PathVariable UUID id,
                                        @Parameter(hidden = true) @CurrentUser UserId adminId) {
        users.unblockUser(UserId.from(id), adminId);
        return ResponseEntity.noContent().build();
    }

    // ────────── mappers ──────────

    UserDtos.UserResponse toResp(User u) {
        return new UserDtos.UserResponse(
                u.id().uuid(),
                u.email().value(),
                u.fullName(),
                u.phoneNumber(),
                u.kycLevel().name(),
                u.status().name(),
                u.emailVerified(),
                u.referralCode(),
                u.referredBy() != null ? u.referredBy().uuid() : null,
                u.roles(),
                u.createdAt(),
                u.updatedAt()
        );
    }

    private UserDtos.UserProfileResponse toProfileResp(User u, UserProfile p, TierLimitsPort.TierLimit tl) {
        UserDtos.TierLimitView tlv = null;
        if (tl != null) {
            tlv = new UserDtos.TierLimitView(
                    tl.kycLevel() != null ? tl.kycLevel().name() : null,
                    tl.countryISO2(),
                    tl.currency() != null ? tl.currency().isoCode() : null,
                    tl.maxSingleTx(),
                    tl.maxDailyVolume(),
                    tl.maxMonthlyVolume(),
                    tl.maxWalletBalance(),
                    tl.maxTopupsPerDay(),
                    tl.maxWithdrawalsPerDay(),
                    tl.withdrawalTierFeeBps()
            );
        }
        return new UserDtos.UserProfileResponse(
                u.id().uuid(),
                p.language(),
                p.theme() != null ? p.theme().name() : null,
                p.preferredCurrency(),
                p.timezone(),
                p.pushEnabled(),
                p.emailNotifEnabled(),
                p.marketingOptIn(),
                p.addressLine(),
                p.addressCity(),
                p.addressCountry(),
                p.billingTaxId(),
                p.profilePictureUrl(),
                p.bio(),
                p.referralBonusClaimed(),
                tlv,
                p.createdAt(),
                p.updatedAt()
        );
    }

    private UserDtos.KycSubmissionResponse toKycResp(KycProviderPort.KycSubmissionResult r) {
        return new UserDtos.KycSubmissionResponse(
                r.providerRef(),
                r.document() != null ? r.document().type() : null,
                r.document() != null ? r.document().number() : null,
                r.document() != null ? r.document().country() : null,
                r.status() != null ? r.status().name() : null,
                r.achievedLevel() != null ? r.achievedLevel().name() : null,
                r.rejectionReason(),
                r.reviewedAt()
        );
    }
}
