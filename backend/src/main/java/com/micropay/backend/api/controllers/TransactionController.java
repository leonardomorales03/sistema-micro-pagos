package com.micropay.backend.api.controllers;

import com.micropay.backend.api.dtos.TransactionDtos;
import com.micropay.backend.domain.entities.AccountMove;
import com.micropay.backend.domain.entities.Transaction;
import com.micropay.backend.domain.exceptions.TransactionNotFoundException;
import com.micropay.backend.domain.ports.in.TransactionUseCases;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Transactions", description = "Transferencias P2P + Historial wallet")
@RestController
@RequestMapping("/api/v1/transactions")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionUseCases tx;

    public TransactionController(TransactionUseCases tx) { this.tx = tx; }

    @PostMapping("/transfer")
    @Operation(summary = "Transferencia P2P entre wallets",
               description = "ACID: Wallet distributed lock + double entry ledger. " +
                       "Retorna 409 si hay conflicto de concurrencia (reintento sugerido).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transferencia exitosa"),
            @ApiResponse(responseCode = "400", description = "Mismos wallets / monto <= 0"),
            @ApiResponse(responseCode = "409", description = "Conflicto lock distribuido, reintente"),
            @ApiResponse(responseCode = "422", description = "Saldo insuficiente / moneda distinta / regla negocio violada")
    })
    public ResponseEntity<TransactionDtos.TransactionResponse> transferP2P(@RequestBody TransactionDtos.TransferRequest req,
                                                                            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        Currency cur = Currency.valueOf(req.currency().toUpperCase());
        Transaction result = tx.transferP2P(
                WalletId.from(req.walletFromId()),
                WalletId.from(req.walletToId()),
                Money.of(req.amount(), cur),
                currentUser,
                req.note()
        );
        return ResponseEntity.ok(toResp(result, true));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar transacción por ID")
    public ResponseEntity<TransactionDtos.TransactionResponse> findById(@PathVariable UUID id) {
        Transaction t = tx.findById(TransactionId.from(id))
                .orElseThrow(() -> new TransactionNotFoundException(id));
        return ResponseEntity.ok(toResp(t, true));
    }

    @GetMapping(params = "short_code")
    @Operation(summary = "Buscar transacción por short_code (QR Payments)")
    public ResponseEntity<TransactionDtos.TransactionResponse> findByShortCode(@RequestParam("short_code") String shortCode) {
        Transaction t = tx.findByShortCode(new ShortCode(shortCode))
                .orElseThrow(() -> new TransactionNotFoundException("short_code=" + shortCode));
        return ResponseEntity.ok(toResp(t, true));
    }

    @GetMapping
    @Operation(summary = "Listar transacciones de una wallet (paginado)")
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
