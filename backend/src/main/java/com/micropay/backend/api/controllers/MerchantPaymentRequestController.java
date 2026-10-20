package com.micropay.backend.api.controllers;

import com.micropay.backend.api.dtos.PaymentRequestDtos;
import com.micropay.backend.application.services.PaymentRequestService;
import com.micropay.backend.domain.entities.PaymentRequest;
import com.micropay.backend.domain.valueobjects.Currency;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "Merchant Payment Requests", description = "Creación y gestión de requests de pago (links/QR) por parte del comercio")
@RestController
@RequestMapping("/api/v1/merchant/payment-requests")
@SecurityRequirement(name = "bearerAuth")
public class MerchantPaymentRequestController {

    private final PaymentRequestService paymentRequests;

    public MerchantPaymentRequestController(PaymentRequestService paymentRequests) {
        this.paymentRequests = paymentRequests;
    }

    @Operation(summary = "Crear PaymentRequest",
               description = "Genera un shortCode único y devuelve el link corto + payload QR. Estado inicial PENDING. " +
                             "Expiración por defecto +24h si no se especifica.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Request creado (PENDING)."),
            @ApiResponse(responseCode = "404", description = "Wallet no existe."),
            @ApiResponse(responseCode = "422", description = "Wallet no propia/inactiva, moneda distinta o monto inválido.")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<PaymentRequestDtos.PaymentRequestResponse> create(
            @Valid @RequestBody PaymentRequestDtos.CreatePaymentRequestRequest req,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        Currency cur = Currency.valueOf(req.currency().toUpperCase());
        Money amount = Money.of(req.amount() == null ? BigDecimal.ZERO : req.amount(), cur);
        PaymentRequest pr = paymentRequests.createRequest(
                currentUser,
                WalletId.from(req.walletId()),
                amount,
                req.description(),
                req.orderReference(),
                req.expiresAt(),
                req.webhookUrl());
        return ResponseEntity.status(201).body(toResp(pr));
    }

    @Operation(summary = "Listar mis PaymentRequests (comercio autenticado)")
    @GetMapping
    public ResponseEntity<PaymentRequestDtos.PaymentRequestListResponse> listMine(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        List<PaymentRequest> rows = paymentRequests.listByMerchant(currentUser, page, pageSize);
        return ResponseEntity.ok(new PaymentRequestDtos.PaymentRequestListResponse(
                rows.stream().map(this::toResp).toList(), page, pageSize));
    }

    // ───────────────── Mapper ─────────────────

    PaymentRequestDtos.PaymentRequestResponse toResp(PaymentRequest pr) {
        return new PaymentRequestDtos.PaymentRequestResponse(
                pr.id().uuid(),
                pr.merchantUserId().uuid(),
                pr.merchantWalletId().uuid(),
                pr.amount().amount(),
                pr.amount().currency().isoCode(),
                pr.description(),
                pr.orderReference(),
                pr.shortCode().value(),
                paymentRequests.shortUrl(pr),
                paymentRequests.qrPayload(pr),
                pr.status().name(),
                pr.paidWithTxId() == null ? null : pr.paidWithTxId().uuid(),
                pr.expiresAt(),
                pr.createdAt(),
                pr.paidAt()
        );
    }
}
