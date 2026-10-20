package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.WalletId;

import java.util.Optional;

/**
 * Port OUT — Proveedor pagos (Stripe). Sandbox lo reemplaza por MockStripeAdapter.
 * Operaciones: crear Checkout TopUp, confirmar, payout withdraw, webhook firmado.
 */
public interface PaymentProviderAdapter {

    record PaymentIntentResult(String externalId, String clientSecret, String status, Money amount) {}

    record PayoutResult(String externalId, String status, Money amount) {}

    /**
     * Nuevo en T17: Checkout navegable (URL) para flujo redirect-backend.
     * Retorna externalId + checkoutUrl.
     */
    record CheckoutResult(String providerRef, String checkoutUrl, Money amount) {}

    /** Evento parseado desde payload webhook. */
    enum WebhookKind { TOPUP_SUCCEEDED, TOPUP_FAILED, TOPUP_REFUNDED, UNKNOWN }
    record ParsedWebhook(WebhookKind kind, String providerRef, Money paidAmount, String failureReason) {}

    PaymentIntentResult createTopUpIntent(Money amount, WalletId walletId);

    /** T17 — creación de checkout URL (estilo Stripe checkout session redirectable). */
    CheckoutResult createCheckout(Money amount,
                                  WalletId walletId,
                                  String successUrl,
                                  String cancelUrl,
                                  String idempotencyKey);

    PayoutResult createWithdrawPayout(Money amount,
                                      WalletId fromWalletId,
                                      String externalAccountId);

    /** Firma/valida un webhook. Retorna true si la firma es válida. */
    boolean validateWebhook(String payload, String signatureHeader);

    /** Parsea payload (JSON) en evento conocido. Devuelve empty si no reconocible. */
    Optional<ParsedWebhook> parseWebhook(String payload);
}
