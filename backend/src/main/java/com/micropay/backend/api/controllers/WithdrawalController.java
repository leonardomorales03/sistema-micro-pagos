package com.micropay.backend.api.controllers;

import com.micropay.backend.api.dtos.WithdrawalDtos;
import com.micropay.backend.application.services.WithdrawalService;
import com.micropay.backend.domain.entities.Withdrawal;
import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.PayoutDestinationId;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;
import com.micropay.backend.domain.valueobjects.WithdrawalId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Tag(name = "Withdrawals", description = "Retiros a cuenta bancaria (PayoutDestination) vía proveedor sandbox")
@RestController
@RequestMapping("/api/v1/withdrawals")
@SecurityRequirement(name = "bearerAuth")
public class WithdrawalController {

    private final WithdrawalService withdrawals;

    public WithdrawalController(WithdrawalService withdrawals) { this.withdrawals = withdrawals; }

    @Operation(summary = "Solicitar retiro",
               description = "Valida KYC ≥ LEVEL_1 + destino verificado, RESERVA fondos en la wallet e instruye el payout provider. " +
                             "Los fondos se retienen hasta que el payout se complete; si falla se liberan.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Retiro creado (PROCESSING)."),
            @ApiResponse(responseCode = "400", description = "Datos inválidos, KYC insuficiente o destino no verificado."),
            @ApiResponse(responseCode = "404", description = "Wallet/destino/usuario no existe."),
            @ApiResponse(responseCode = "428", description = "Challenge de riesgo requerido (step-up)")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<WithdrawalDtos.WithdrawalResponse> create(
            @Valid @RequestBody WithdrawalDtos.CreateWithdrawalRequest req,
            @Parameter(hidden = true) @CurrentUser UserId currentUser,
            @RequestHeader(value = "X-Risk-Challenge", required = false) String challengeToken) {
        Currency cur = Currency.valueOf(req.currency().toUpperCase());
        Money m = Money.of(req.amount() == null ? BigDecimal.ZERO : req.amount(), cur);
        Withdrawal w = withdrawals.requestWithdrawal(currentUser,
                WalletId.from(req.walletId()),
                PayoutDestinationId.from(req.payoutDestinationId()),
                m, req.idempotencyKey(), challengeToken);
        return ResponseEntity.status(201).body(toResp(w));
    }

    @Operation(summary = "Consultar retiro por ID (owner o ADMIN)")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @walletSecurity.isOwner(" +
            "@withdrawalController.walletIdForWithdrawal(#id), authentication)")
    public ResponseEntity<WithdrawalDtos.WithdrawalResponse> findById(@PathVariable UUID id) {
        return withdrawals.findById(WithdrawalId.from(id))
                .map(w -> ResponseEntity.ok(toResp(w)))
                .orElseThrow(() -> new BusinessRuleViolationException("WITHDRAWAL_NOT_FOUND", "Withdrawal " + id + " no encontrado"));
    }

    @Operation(summary = "Listar retiros de la wallet (owner o ADMIN)")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or @walletSecurity.isOwner(#walletId.toString(), authentication)")
    public ResponseEntity<WithdrawalDtos.WithdrawalListResponse> listByWallet(
            @RequestParam("wallet_id") UUID walletId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        List<Withdrawal> rows = withdrawals.listByWallet(WalletId.from(walletId), currentUser, page, pageSize, false);
        return ResponseEntity.ok(new WithdrawalDtos.WithdrawalListResponse(
                rows.stream().map(this::toResp).toList(), page, pageSize));
    }

    @Operation(summary = "Listar mis retiros")
    @GetMapping("/me")
    public ResponseEntity<WithdrawalDtos.WithdrawalListResponse> listMine(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        List<Withdrawal> rows = withdrawals.listByUser(currentUser, page, pageSize);
        return ResponseEntity.ok(new WithdrawalDtos.WithdrawalListResponse(
                rows.stream().map(this::toResp).toList(), page, pageSize));
    }

    /** Helper SpEL (wallet ownership check vía @walletSecurity bean). */
    @SuppressWarnings("unused")
    public String walletIdForWithdrawal(UUID withdrawalId) {
        return withdrawals.findById(WithdrawalId.from(withdrawalId))
                .map(w -> w.walletId().toString())
                .orElse(null);
    }

    // ──────── Sandbox endpoints PÚBLICOS: resolver payout sin webhook externo ────────

    @Operation(summary = "Sandbox-only: simular payout COMPLETED",
               description = "Endpoint público solo dev. Consume el hold (confirmReserveAsDebit) y marca COMPLETED.")
    @GetMapping("/sandbox/complete/{providerRef}")
    public ResponseEntity<WithdrawalDtos.WithdrawalResponse> sandboxComplete(@PathVariable String providerRef) {
        return ResponseEntity.ok(toResp(withdrawals.sandboxCompleteByProviderRef(providerRef)));
    }

    @Operation(summary = "Sandbox-only: simular payout FAILED",
               description = "Endpoint público solo dev. Libera el hold (releaseReserve) y marca FAILED.")
    @GetMapping("/sandbox/fail/{providerRef}")
    public ResponseEntity<WithdrawalDtos.WithdrawalResponse> sandboxFail(
            @PathVariable String providerRef,
            @RequestParam(value = "reason", defaultValue = "PAYOUT_FAILED_SANDBOX") String reason) {
        return ResponseEntity.ok(toResp(withdrawals.sandboxFailByProviderRef(providerRef, reason)));
    }

    // ───────────────── Mapper ─────────────────

    private WithdrawalDtos.WithdrawalResponse toResp(Withdrawal w) {
        return new WithdrawalDtos.WithdrawalResponse(
                w.id().uuid(),
                w.userId().uuid(),
                w.walletId().uuid(),
                w.payoutDestinationId().uuid(),
                w.grossAmount().amount(),
                w.netAmount().amount(),
                w.feeAmount().amount(),
                w.grossAmount().currency().isoCode(),
                w.provider(),
                w.providerRef(),
                w.status().name(),
                w.failureReason(),
                w.note(),
                w.requestedAt(),
                w.processedAt(),
                w.requestedAt(),
                w.updatedAt()
        );
    }
}
