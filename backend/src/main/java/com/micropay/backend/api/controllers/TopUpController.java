package com.micropay.backend.api.controllers;

import com.micropay.backend.api.dtos.TopUpDtos;
import com.micropay.backend.application.services.TopUpService;
import com.micropay.backend.domain.entities.TopUp;
import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.TopUpId;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;
import com.micropay.backend.api.controllers.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Tag(name = "TopUps", description = "Recargas (Top-Up) con proveedor pagos sandbox Stripe")
@RestController
@RequestMapping("/api/v1/top-ups")
@SecurityRequirement(name = "bearerAuth")
public class TopUpController {

    private final TopUpService topUps;
    private final boolean multiCurrencyEnabled;

    public TopUpController(TopUpService topUps,
                           @Value("${micropay.multicurrency.enabled:false}") boolean multiCurrencyEnabled) {
        this.topUps = topUps;
        this.multiCurrencyEnabled = multiCurrencyEnabled;
    }

    @Operation(summary = "Crear intento de recarga (checkout proveedor)",
               description = "Crea un TopUp PENDING_PAYMENT + checkout URL del proveedor. Moneda debe coincidir con moneda wallet.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Checkout creado OK. checkout_url navegable."),
            @ApiResponse(responseCode = "400", description = "Monto/moneda inválida o wallet no perteneciente."),
            @ApiResponse(responseCode = "404", description = "Wallet no existe."),
            @ApiResponse(responseCode = "428", description = "Challenge de riesgo requerido (step-up)")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<TopUpDtos.TopUpResponse> create(
            @Valid @RequestBody TopUpDtos.CreateTopUpRequest req,
            @Parameter(hidden = true) @CurrentUser UserId currentUser,
            @RequestHeader(value = "X-Risk-Challenge", required = false) String challengeToken) {
        String iso = (req.currency() == null || req.currency().isBlank()) ? "COP" : req.currency();
        Currency cur = Currency.fromIso(iso);
        if (!cur.equals(Currency.COP) && !multiCurrencyEnabled) {
            throw new BusinessRuleViolationException("MULTICURRENCY_DISABLED",
                    "La moneda %s no está permitida sin el flag multi-moneda activo".formatted(cur));
        }
        Money m = Money.of(req.amount() == null ? BigDecimal.ZERO : req.amount(), cur);
        TopUp t = topUps.createCheckout(currentUser,
                WalletId.from(req.walletId()), m,
                req.successUrl(), req.cancelUrl(), req.idempotencyKey(), challengeToken);
        return ResponseEntity.status(201).body(toResp(t));
    }

    @Operation(summary = "Consultar recarga por ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @walletSecurity.isOwner(" +
            "@topUpController.walletIdForTopUp(#id).toString(), authentication)")
    public ResponseEntity<TopUpDtos.TopUpResponse> findById(@PathVariable UUID id) {
        return topUps.findById(TopUpId.from(id))
                .map(t -> ResponseEntity.ok(toResp(t)))
                .orElseThrow(() -> new BusinessRuleViolationException("TOPUP_NOT_FOUND", "TopUp " + id + " no encontrado"));
    }

    @Operation(summary = "Listar recargas de wallet (owner o ADMIN)")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or @walletSecurity.isOwner(#walletId.toString(), authentication)")
    public ResponseEntity<TopUpDtos.TopUpListResponse> listByWallet(
            @RequestParam("wallet_id") UUID walletId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        boolean admin = currentUser != null && false; // admin check handled en service via @PreAuthorize wallet ADMIN path
        List<TopUp> rows = topUps.listByWallet(WalletId.from(walletId), currentUser, page, pageSize, admin);
        return ResponseEntity.ok(new TopUpDtos.TopUpListResponse(
                rows.stream().map(this::toResp).toList(), page, pageSize));
    }

    /** Helper SpEL (wallet ownership check vía @walletSecurity bean). */
    @SuppressWarnings("unused")
    public WalletId walletIdForTopUp(UUID topUpId) {
        return topUps.findById(TopUpId.from(topUpId))
                .map(TopUp::walletId)
                .orElse(null);
    }

    // ──────── Sandbox endpoint PÚBLICO: aprobar checkout mock (redirige a success_url si existe) ────────
    @Operation(summary = "Sandbox-only: aprobar checkout mock desde provider redirect",
               description = "Endpoint público solo dev. El proveedor 'mock' redirige aquí → procesa webhook succeeded → redirige a success_url del TopUp.")
    @GetMapping("/mock-approve/{providerRef}")
    public RedirectView sandboxMockApprove(@PathVariable String providerRef) {
        TopUp t = topUps.sandboxApproveByProviderRef(providerRef);
        String redirect = t.successUrl() != null && !t.successUrl().isBlank()
                ? t.successUrl()
                : "/api/v1/system/info";
        return new RedirectView(redirect);
    }

    // ───────────────── Mapper ─────────────────
    private TopUpDtos.TopUpResponse toResp(TopUp t) {
        return new TopUpDtos.TopUpResponse(
                t.id().uuid(),
                t.userId().uuid(),
                t.walletId().uuid(),
                t.grossAmount().amount(),
                t.netAmount().amount(),
                t.feeAmount().amount(),
                t.grossAmount().currency().isoCode(),
                t.provider(),
                t.providerRef(),
                t.checkoutUrl(),
                t.status().name(),
                t.failureReason(),
                t.paidAt(),
                t.transactionRef() != null ? t.transactionRef().uuid() : null,
                t.createdAt(),
                t.updatedAt()
        );
    }
}
