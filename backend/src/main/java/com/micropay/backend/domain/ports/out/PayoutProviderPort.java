package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.valueobjects.Money;

import java.util.Optional;

/**
 * Port OUT — Proveedor de payouts (retiros a cuenta bancaria).
 * Sandbox: StubStripePayoutProviderAdapter (auto-confirma o falla según payload).
 *
 * Modelo de estados del proveedor: INITIATED → COMPLETED | FAILED
 */
public interface PayoutProviderPort {

    /** Resultado de iniciar un payout. */
    record PayoutInitResult(String providerRef, String status) {}

    /** Evento de actualización (webhook o polling). */
    enum PayoutEventKind { PAYOUT_COMPLETED, PAYOUT_FAILED, PAYOUT_RETURNED, UNKNOWN }
    record ParsedPayoutEvent(PayoutEventKind kind, String providerRef, Money amount, String failureReason) {}

    /**
     * Inicia un payout hacia una cuenta externa. NO mueve fondos: sólo instruye al proveedor.
     * La reserva/devolución de fondos es responsabilidad del dominio (Wallet).
     */
    PayoutInitResult initiatePayout(Money amount, String maskedAccountLabel, String idempotencyKey);

    /** Valida firma del webhook del proveedor. */
    boolean validateWebhook(String payload, String signatureHeader);

    /** Parsea el payload del webhook/polling en un evento conocido. */
    Optional<ParsedPayoutEvent> parseWebhook(String payload);
}
