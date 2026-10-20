package com.micropay.backend.api.controllers;

import com.micropay.backend.api.dtos.PaymentRequestDtos;
import com.micropay.backend.application.services.PaymentRequestService;
import com.micropay.backend.domain.entities.PaymentRequest;
import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.valueobjects.PaymentRequestId;
import com.micropay.backend.domain.valueobjects.ShortCode;
import com.micropay.backend.domain.valueobjects.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@Tag(name = "Public Payment Requests", description = "Acceso público al link/QR de pago y confirmación de pago por el cliente")
@RestController
@RequestMapping("/api/v1/public/payment-requests")
public class PublicPaymentRequestController {

    private final PaymentRequestService paymentRequests;

    public PublicPaymentRequestController(PaymentRequestService paymentRequests) {
        this.paymentRequests = paymentRequests;
    }

    @Operation(summary = "Ver PaymentRequest por shortCode (público)",
               description = "El cliente abre el link/QR y ve los detalles del cobro. Si expiró sin pagarse, " +
                             "el status efectivo devuelto es EXPIRED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalle del request."),
            @ApiResponse(responseCode = "422", description = "shortCode no encontrado.")
    })
    @GetMapping("/{shortCode}")
    public ResponseEntity<PaymentRequestDtos.PublicPaymentRequestResponse> getByShortCode(
            @PathVariable String shortCode) {
        PaymentRequest pr = paymentRequests.findByShortCode(new ShortCode(shortCode))
                .orElseThrow(() -> new BusinessRuleViolationException("PAYMENT_REQUEST_NOT_FOUND",
                        "PaymentRequest con shortCode=" + shortCode + " no encontrado"));
        return ResponseEntity.ok(toPublic(pr));
    }

    @Operation(summary = "Pagar PaymentRequest (cliente autenticado)",
               description = "Ejecuta la transferencia P2P atómica de la wallet del cliente a la del comercio, " +
                             "marca el request PAID y devuelve el recibo con el transaction ID. " +
                             "Idempotente a nivel request: un segundo intento no vuelve a mover fondos.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pago confirmado (o replay idempotente)."),
            @ApiResponse(responseCode = "404", description = "Request/USER no existe."),
            @ApiResponse(responseCode = "422", description = "Request no pagable (expirado/cancelado), sin wallet, self-pay o límite excedido.")
    })
    @PostMapping("/{id}/pay")
    public ResponseEntity<PaymentRequestDtos.PayPaymentRequestResponse> pay(
            @PathVariable UUID id,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        PaymentRequestService.PayResult result = paymentRequests.pay(currentUser, PaymentRequestId.from(id));
        PaymentRequest pr = result.request();
        UUID txId = result.transaction() == null ? (pr.paidWithTxId() == null ? null : pr.paidWithTxId().uuid())
                                                 : result.transaction().id().uuid();
        return ResponseEntity.ok(new PaymentRequestDtos.PayPaymentRequestResponse(
                pr.id().uuid(),
                pr.shortCode().value(),
                pr.status().name(),
                txId,
                pr.amount().amount(),
                pr.amount().currency().isoCode(),
                result.idempotentReplay()));
    }

    // ───────────────── Mapper ─────────────────

    private PaymentRequestDtos.PublicPaymentRequestResponse toPublic(PaymentRequest pr) {
        String effectiveStatus = (pr.isPending() && pr.isExpiredAt(Instant.now()))
                ? "EXPIRED"
                : pr.status().name();
        return new PaymentRequestDtos.PublicPaymentRequestResponse(
                pr.id().uuid(),
                pr.shortCode().value(),
                pr.amount().amount(),
                pr.amount().currency().isoCode(),
                pr.description(),
                effectiveStatus,
                pr.expiresAt(),
                pr.createdAt()
        );
    }
}
