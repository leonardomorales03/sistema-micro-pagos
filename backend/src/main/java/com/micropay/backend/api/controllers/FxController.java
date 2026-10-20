package com.micropay.backend.api.controllers;

import com.micropay.backend.api.dtos.FxDtos;
import com.micropay.backend.application.services.WalletService;
import com.micropay.backend.domain.entities.AccountMove;
import com.micropay.backend.domain.entities.Transaction;
import com.micropay.backend.domain.entities.Wallet;
import com.micropay.backend.domain.ports.in.FxUseCases;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.FxQuote;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Endpoints de multi-moneda / FX (T26 / Requisito 29.6).
 * Con el flag multi-moneda OFF, {@code POST /convert} responde 422 (MULTICURRENCY_DISABLED).
 */
@Tag(name = "FX", description = "Tipos de cambio y conversión multi-moneda (feature-flagged)")
@RestController
@RequestMapping("/api/v1/fx")
@SecurityRequirement(name = "bearerAuth")
public class FxController {

    private final FxUseCases fx;
    private final WalletService wallets;

    public FxController(FxUseCases fx, WalletService wallets) {
        this.fx = fx;
        this.wallets = wallets;
    }

    @Operation(summary = "Listar tasas desde una moneda base",
               description = "Devuelve la cotización de la moneda base contra todas las monedas soportadas.")
    @ApiResponse(responseCode = "200", description = "Tasas disponibles")
    @GetMapping("/rates")
    public ResponseEntity<FxDtos.RatesResponse> rates(
            @RequestParam(value = "base", defaultValue = "COP") String base) {
        Currency b = Currency.fromIso(base);
        List<FxDtos.RateEntry> rates = Currency.supported().stream()
                .filter(c -> c != b)
                .map(q -> {
                    FxQuote quote = fx.getQuote(b, q);
                    return new FxDtos.RateEntry(b.isoCode(), q.isoCode(),
                            quote.rate(), quote.source(), quote.fetchedAt());
                })
                .toList();
        return ResponseEntity.ok(new FxDtos.RatesResponse(b.isoCode(), rates));
    }

    @Operation(summary = "Convertir saldo entre monedas del mismo usuario",
               description = "Débito en el wallet origen + crédito en el wallet destino (misma moneda destino). Requiere flag multi-moneda ON.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conversión completada"),
            @ApiResponse(responseCode = "422", description = "Flag multi-moneda OFF o regla de negocio violada")
    })
    @PostMapping("/convert")
    @PreAuthorize("hasRole('ADMIN') or @walletSecurity.isOwner(#req.walletId.toString(), authentication)")
    public ResponseEntity<FxDtos.ConvertResponse> convert(
            @Valid @RequestBody FxDtos.ConvertRequest req,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        WalletId fromWalletId = WalletId.from(req.walletId());
        Currency source = (req.currency() == null || req.currency().isBlank())
                ? wallets.getByIdOrThrow(fromWalletId).currency()
                : Currency.fromIso(req.currency());
        Currency target = Currency.fromIso(req.targetCurrency());
        Money amount = Money.of(req.amount() == null ? BigDecimal.ZERO : req.amount(), source);
        Transaction tx = fx.convert(fromWalletId, target, amount, currentUser);
        return ResponseEntity.ok(toResp(tx, source, target));
    }

    private FxDtos.ConvertResponse toResp(Transaction tx, Currency source, Currency target) {
        BigDecimal converted = tx.accountMoves().stream()
                .filter(AccountMove::isCredit)
                .map(m -> m.amount().amount())
                .findFirst()
                .orElse(null);
        return new FxDtos.ConvertResponse(
                tx.id().uuid(),
                tx.shortCode() != null ? tx.shortCode().value() : null,
                tx.type().name(),
                tx.status().name(),
                source.isoCode(),
                target.isoCode(),
                tx.grossAmount().amount(),
                converted,
                decimalMetadata(tx, "fxRate"),
                stringMetadata(tx, "fxSource"),
                tx.createdAt());
    }

    private BigDecimal decimalMetadata(Transaction tx, String key) {
        Object v = tx.metadata().get(key);
        return v == null ? null : new BigDecimal(v.toString());
    }

    private String stringMetadata(Transaction tx, String key) {
        Object v = tx.metadata().get(key);
        return v == null ? null : v.toString();
    }
}
