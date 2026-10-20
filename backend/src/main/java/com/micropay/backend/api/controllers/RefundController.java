package com.micropay.backend.api.controllers;

import com.micropay.backend.api.dtos.RefundDtos;
import com.micropay.backend.application.services.RefundService;
import com.micropay.backend.domain.entities.Refund;
import com.micropay.backend.domain.entities.Transaction;
import com.micropay.backend.domain.exceptions.TransactionNotFoundException;
import com.micropay.backend.domain.ports.in.TransactionUseCases;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.TransactionId;
import com.micropay.backend.domain.valueobjects.UserId;
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

/**
 * Endpoints de Reembolsos (Requirement 23).
 * Público objetivo: comercio receptor (role MERCHANT) o ADMIN que reembolsa una transacción COMPLETED.
 */
@Tag(name = "Refunds", description = "Reembolsos totales/parciales de transacciones completadas")
@RestController
@RequestMapping("/api/v1/transactions/{txId}/refunds")
@SecurityRequirement(name = "bearerAuth")
public class RefundController {

    private final RefundService refunds;
    private final TransactionUseCases tx;

    public RefundController(RefundService refunds, TransactionUseCases tx) {
        this.refunds = refunds;
        this.tx = tx;
    }

    @Operation(summary = "Crear reembolso (total o parcial)",
               description = "Ejecuta un reverse transfer atómico hacia el pagador original. " +
                       "El acumulado de reembolsos no puede exceder el monto original (Property 26). " +
                       "Si el receptor no tiene saldo disponible → 422 sin efectos secundarios.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reembolso registrado"),
            @ApiResponse(responseCode = "403", description = "No autorizado (no es receptor ni admin)"),
            @ApiResponse(responseCode = "404", description = "Transacción origen no encontrada"),
            @ApiResponse(responseCode = "409", description = "Conflicto de concurrencia, reintente"),
            @ApiResponse(responseCode = "422", description = "Tipo/estado no reembolsable, excede restante o saldo insuficiente")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ROLE_ADMIN') or @walletSecurity.isOwner(@refundController.recipientWalletId(#txId), authentication)")
    public ResponseEntity<RefundDtos.RefundResponse> create(
            @PathVariable UUID txId,
            @Valid @RequestBody RefundDtos.CreateRefundRequest req,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        Currency cur = Currency.valueOf(req.currency().toUpperCase());
        Money amount = Money.of(req.refundedAmount() == null ? BigDecimal.ZERO : req.refundedAmount(), cur);
        RefundService.RefundResult result = refunds.createRefund(
                currentUser, TransactionId.from(txId), amount, req.reason());
        return ResponseEntity.status(201).body(toResp(result.refund()));
    }

    @Operation(summary = "Listar historial de reembolsos de una transacción (receptor o ADMIN)")
    @GetMapping
    @PreAuthorize("hasRole('ROLE_ADMIN') or @walletSecurity.isOwner(@refundController.recipientWalletId(#txId), authentication)")
    public ResponseEntity<RefundDtos.RefundListResponse> list(@PathVariable UUID txId) {
        List<Refund> rows = refunds.listBySourceTransaction(TransactionId.from(txId));
        return ResponseEntity.ok(new RefundDtos.RefundListResponse(
                rows.stream().map(this::toResp).toList(), rows.size()));
    }

    /** Helper SpEL: wallet receptora (wallet_to_id) de la transacción origen. */
    @SuppressWarnings("unused")
    public UUID recipientWalletId(UUID txId) {
        return tx.findById(TransactionId.from(txId))
                .map(Transaction::walletToId)
                .map(w -> w != null ? w.uuid() : null)
                .orElse(null);
    }

    // ───────── mapper ─────────

    private RefundDtos.RefundResponse toResp(Refund r) {
        return new RefundDtos.RefundResponse(
                r.id().uuid(),
                r.sourceTransactionId().uuid(),
                r.refundingUserId().uuid(),
                r.refundedAmount().amount(),
                r.refundedAmount().currency().isoCode(),
                r.type().name(),
                r.reason(),
                r.refundTxId().uuid(),
                r.createdAt()
        );
    }
}
