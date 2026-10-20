package com.micropay.backend.api.controllers;

import com.micropay.backend.api.dtos.TransactionDtos;
import com.micropay.backend.application.services.TransactionService;
import com.micropay.backend.domain.entities.AccountMove;
import com.micropay.backend.domain.entities.Transaction;
import com.micropay.backend.domain.exceptions.TransactionNotFoundException;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.ShortCode;
import com.micropay.backend.domain.valueobjects.TransactionId;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Transactions", description = "Transferencias P2P + Historial wallet")
@RestController
@RequestMapping("/api/v1/transactions")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService tx;

    public TransactionController(TransactionService tx) { this.tx = tx; }

    @PostMapping("/transfer")
    @Operation(summary = "Transferencia P2P entre wallets",
               description = "ACID: Wallet distributed lock + double entry ledger. " +
                       "Retorna 409 si hay conflicto de concurrencia (reintento sugerido). " +
                       "Puede retornar 428 si el RiskEngine exige step-up (header X-Risk-Challenge-Token).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transferencia exitosa"),
            @ApiResponse(responseCode = "400", description = "Mismos wallets / monto <= 0"),
            @ApiResponse(responseCode = "409", description = "Conflicto lock distribuido, reintente"),
            @ApiResponse(responseCode = "422", description = "Saldo insuficiente / moneda distinta / regla negocio violada"),
            @ApiResponse(responseCode = "428", description = "Challenge de riesgo requerido (step-up)")
    })
    @PreAuthorize("hasRole('ADMIN') or @walletSecurity.isOwner(#req.walletFromId.toString(), authentication)")
    public ResponseEntity<TransactionDtos.TransactionResponse> transferP2P(
            @RequestBody TransactionDtos.TransferRequest req,
            @Parameter(hidden = true) @CurrentUser UserId currentUser,
            @RequestHeader(value = "X-Risk-Challenge", required = false) String challengeToken) {
        Currency cur = Currency.valueOf(req.currency().toUpperCase());
        Transaction result = tx.transferP2P(
                WalletId.from(req.walletFromId()),
                WalletId.from(req.walletToId()),
                Money.of(req.amount(), cur),
                currentUser,
                req.note(),
                challengeToken
        );
        return ResponseEntity.ok(toResp(result, true));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar transacción por ID (ADMIN o participant wallet owner)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TransactionDtos.TransactionResponse> findById(@PathVariable UUID id) {
        Transaction t = tx.findById(TransactionId.from(id))
                .orElseThrow(() -> new TransactionNotFoundException(id));
        return ResponseEntity.ok(toResp(t, true));
    }

    @GetMapping(params = "short_code")
    @Operation(summary = "Buscar transacción por short_code (QR Payments)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TransactionDtos.TransactionResponse> findByShortCode(@RequestParam("short_code") String shortCode) {
        Transaction t = tx.findByShortCode(new ShortCode(shortCode))
                .orElseThrow(() -> new TransactionNotFoundException("short_code=" + shortCode));
        return ResponseEntity.ok(toResp(t, true));
    }

    @GetMapping
    @Operation(summary = "Listar transacciones de una wallet (paginado — wallet owner o ADMIN)")
    @PreAuthorize("hasRole('ADMIN') or @walletSecurity.isOwner(#walletId.toString(), authentication)")
    public ResponseEntity<List<TransactionDtos.TransactionResponse>> listByWallet(
            @RequestParam("wallet_id") UUID walletId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        List<Transaction> items = tx.listTransactions(WalletId.from(walletId), page, pageSize);
        return ResponseEntity.ok(items.stream().map(x -> toResp(x, false)).toList());
    }

    // ───────── mapper ─────────
    TransactionDtos.TransactionResponse toResp(Transaction t, boolean includeMoves) {
        List<TransactionDtos.AccountMoveResponse> moves = null;
        if (includeMoves && t.accountMoves() != null) {
            moves = t.accountMoves().stream().map(this::toMoveResp).toList();
        }
        return new TransactionDtos.TransactionResponse(
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
                t.externalRef(),
                t.note(),
                t.failureReason(),
                t.createdBy(),
                t.createdAt(),
                t.updatedAt(),
                moves
        );
    }

    TransactionDtos.AccountMoveResponse toMoveResp(AccountMove m) {
        return new TransactionDtos.AccountMoveResponse(
                m.id(),
                m.walletId().uuid(),
                m.transactionId().uuid(),
                m.amount().amount(),
                m.balanceAfter().amount(),
                m.createdAt()
        );
    }
}
