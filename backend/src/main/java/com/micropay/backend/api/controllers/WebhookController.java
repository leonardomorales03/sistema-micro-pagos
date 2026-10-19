package com.micropay.backend.api.controllers;

import com.micropay.backend.application.services.TopUpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Webhooks", description = "Endpoints públicos webhooks proveedores externos (Stripe, etc.)")
@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final TopUpService topUps;

    public WebhookController(TopUpService topUps) { this.topUps = topUps; }

    @Operation(summary = "Stripe / Stripe-Sandbox webhook (TopUps succeeded/failed/refunded)",
               description = "PÚBLICO sin auth. Valida HMAC SHA256 header `Stripe-Signature`;" +
                             " luego parsea y acredita fondos o marca fallido. Idempotente (ya PAID no re-acredita).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Procesado OK (o evento desconocido aceptado sin accionar)."),
            @ApiResponse(responseCode = "401", description = "Firma Stripe-Signature inválida."),
            @ApiResponse(responseCode = "409", description = "TopUp ya PAID con tx distinta (conflicto idempotencia).")
    })
    @PostMapping("/gateways/stripe")
    public ResponseEntity<Void> stripeWebhook(@RequestBody(required = false) String rawBody,
                                              @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature,
                                              @RequestHeader(value = "X-Stripe-Signature", required = false) String xStripeSignature) {
        String sig = stripeSignature != null ? stripeSignature : xStripeSignature;
        try {
            int code = topUps.handleWebhook(rawBody, sig);
            return switch (code) {
                case 1 -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
                case 2 -> ResponseEntity.status(HttpStatus.NO_CONTENT).build();
                default -> ResponseEntity.status(HttpStatus.NO_CONTENT).build();
            };
        } catch (IllegalStateException ise) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (Exception ex) {
            log.warn("[WEBHOOK] stripe handle error: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
}
