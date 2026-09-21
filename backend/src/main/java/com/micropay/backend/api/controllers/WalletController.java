package com.micropay.backend.api.controllers;

import com.micropay.backend.api.dtos.WalletDtos;
import com.micropay.backend.application.services.WalletService;
import com.micropay.backend.domain.entities.Wallet;
import com.micropay.backend.domain.ports.in.TransactionUseCases;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WalletId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Tag(name = "Wallets", description = "Gestión de wallets: crear, listar, consultar saldos")
@RestController
@RequestMapping("/api/v1/wallets")
@SecurityRequirement(name = "bearerAuth")
public class WalletController {

    private final WalletService wallets;
    private final TransactionUseCases transactionUseCases;

    public WalletController(WalletService wallets, TransactionUseCases transactionUseCases) {
        this.wallets = wallets;
        this.transactionUseCases = transactionUseCases;
    }

    @PostMapping
    @Operation(summary = "Crear wallet nueva para el usuario actual en una moneda",
               description = "Devuelve 422 si ya existe wallet en esa moneda para el usuario.")
    @ApiResponse(responseCode = "201", description = "Wallet creada")
    @ApiResponse(responseCode = "409", description = "Wallet ya existe para el par usuario-moneda")
    public ResponseEntity<WalletDtos.WalletResponse> create(@RequestBody WalletDtos.CreateWalletRequest req,
                                                            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        Wallet w = transactionUseCases.createWallet(currentUser, Currency.valueOf(req.currency().toUpperCase()), currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResp(w));
    }

    @GetMapping("/me")
    @Operation(summary = "Listar wallets del usuario autenticado")
    public ResponseEntity<List<WalletDtos.WalletResponse>> listMine(@Parameter(hidden = true) @CurrentUser UserId currentUser) {
        return ResponseEntity.ok(wallets.listByUser(currentUser).stream().map(this::toResp).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN') or @walletSecurity.isOwner(#id, authentication)")
    @Operation(summary = "Obtener wallet por ID (propietario o ADMIN)")
    public ResponseEntity<WalletDtos.WalletResponse> findById(@PathVariable UUID id) {
        Wallet w = wallets.getByIdOrThrow(WalletId.from(id));
        return ResponseEntity.ok(toResp(w));
    }

    @PostMapping("/{id}/freeze")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Congelar wallet (sólo ADMIN)")
    public ResponseEntity<WalletDtos.WalletResponse> freeze(@PathVariable UUID id) {
        return ResponseEntity.ok(toResp(wallets.freeze(WalletId.from(id))));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Cerrar wallet (sólo ADMIN)")
    public ResponseEntity<WalletDtos.WalletResponse> close(@PathVariable UUID id) {
        return ResponseEntity.ok(toResp(wallets.close(WalletId.from(id))));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Activar wallet congelada/cerrada (sólo ADMIN)")
    public ResponseEntity<WalletDtos.WalletResponse> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(toResp(wallets.activate(WalletId.from(id))));
    }

    // ───────── mapper ─────────
    WalletDtos.WalletResponse toResp(Wallet w) {
        BigDecimal available = w.available().amount();
        return new WalletDtos.WalletResponse(
                w.id().uuid(),
                w.userId().uuid(),
                w.currency().isoCode(),
                w.balance().amount(),
                w.reserved().amount(),
                available,
                w.status().name(),
                w.createdBy(),
                w.createdAt(),
                w.updatedAt(),
                w.version()
        );
    }
}
