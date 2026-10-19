package com.micropay.backend.infrastructure.payments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micropay.backend.domain.ports.out.PaymentProviderAdapter;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.WalletId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;

/**
 * Sandbox / Stub del proveedor pagos (Stripe simulado).
 *
 * T17 — añade:
 *  - createCheckout: URL "mock-approve" navegable (GET endpoint controller → POST webhook interno a /api/v1/webhooks/gateways/stripe devuelve 200).
 *  - parseWebhook: JSON con {"type":"payment_intent.succeeded","data":{"object":{"id":"pi_...","amount":100,"currency":"cop","failure_reason":null}}}
 *  - validateWebhook: Stripe-Signature estilo `t=<timestamp>,v1=<hmac-sha256(timestamp.payload, webhook-secret)>`.
 */
@Component
@Profile({"dev", "test", "sandbox"})
public class StubStripePaymentProviderAdapter implements PaymentProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(StubStripePaymentProviderAdapter.class);
    private static final String PREFIX_INTENT = "pi_stub_";
    private static final String PREFIX_PAYOUT = "po_stub_";
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final String webhookSecret;
    private final String baseExternalUrl;
    private final ObjectMapper json;

    public StubStripePaymentProviderAdapter(
            @Value("${micropay.payments.stripe.webhook-secret:stripe-webhook-secret-dev-only}") String webhookSecret,
            @Value("${micropay.payments.sandbox.base-url:http://localhost:8080}") String baseExternalUrl,
            ObjectMapper json) {
        this.webhookSecret = webhookSecret == null ? "stripe-webhook-secret-dev-only" : webhookSecret;
        this.baseExternalUrl = baseExternalUrl == null ? "http://localhost:8080" : baseExternalUrl;
        this.json = json == null ? new ObjectMapper() : json;
    }

    @Override
    public PaymentIntentResult createTopUpIntent(Money amount, WalletId walletId) {
        String id = PREFIX_INTENT + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String clientSecret = id + "_secret_" + walletId;
        log.info("[STUB STRIPE] TopUp Intent creado id={} amount={} wallet={}", id, amount, walletId);
        return new PaymentIntentResult(id, clientSecret, "requires_capture", amount);
    }

    @Override
    public CheckoutResult createCheckout(Money amount,
                                         WalletId walletId,
                                         String successUrl,
                                         String cancelUrl,
                                         String idempotencyKey) {
        String ref = PREFIX_INTENT + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String approveUrl = baseExternalUrl + "/api/v1/top-ups/mock-approve/" + ref;
        log.info("[STUB STRIPE] Checkout creado providerRef={} amount={} walletId={} checkoutUrl={}",
                ref, amount, walletId, approveUrl);
        return new CheckoutResult(ref, approveUrl, amount);
    }

    @Override
    public PayoutResult createWithdrawPayout(Money amount, WalletId fromWalletId, String externalAccountId) {
        String id = PREFIX_PAYOUT + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        log.info("[STUB STRIPE] Payout retiro id={} amount={} from={} account={}",
                id, amount, fromWalletId, externalAccountId);
        return new PayoutResult(id, "pending", amount);
    }

    // ────────── Webhook HMAC SHA256 signature verify (Stripe t=,v1= style) ──────────

    @Override
    public boolean validateWebhook(String payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            log.debug("[STUB STRIPE] Webhook sin firma, SKIP valido");
            return true;
        }
        try {
            String tPart = null;
            String v1Part = null;
            for (String seg : signatureHeader.split(",")) {
                String[] kv = seg.split("=", 2);
                if (kv.length != 2) continue;
                if ("t".equals(kv[0])) tPart = kv[1];
                if ("v1".equals(kv[0])) v1Part = kv[1];
            }
            if (tPart == null || v1Part == null) return true;
            String signedPayload = tPart + "." + (payload == null ? "" : payload);
            String expected = hexHmacSha256(signedPayload, webhookSecret);
            boolean ok = MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                                                v1Part.getBytes(StandardCharsets.UTF_8));
            if (!ok) {
                log.warn("[STUB STRIPE] Webhook firma INVALIDA header={}", signatureHeader);
            }
            return ok;
        } catch (Exception ex) {
            log.warn("[STUB STRIPE] Webhook excepción validación firma: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public Optional<ParsedWebhook> parseWebhook(String payload) {
        if (payload == null || payload.isBlank()) return Optional.empty();
        try {
            JsonNode root = json.readTree(payload);
            String type = root.path("type").asText("");
            JsonNode obj = root.path("data").path("object");
            String id = obj.path("id").asText(null);
            if (id == null) id = root.path("provider_ref").asText(null);
            if (id == null) return Optional.empty();
            BigDecimal minor = obj.path("amount").decimalValue();
            String currencyStr = obj.path("currency").asText("COP").toUpperCase();
            Currency currency;
            try { currency = Currency.valueOf(currencyStr); }
            catch (Exception e) { currency = Currency.COP; }
            Money amount;
            try {
                // minor units → 2 decimales estándar (COP = 0 dec, pero modelamos como 8 decimales en Money; asumimos 2 al parsear)
                int exponent = obj.has("currency_exponent") ? obj.path("currency_exponent").intValue() : 2;
                BigDecimal dollars = minor.movePointLeft(exponent);
                amount = Money.of(dollars, currency);
            } catch (Exception e) {
                amount = Money.of(BigDecimal.ZERO, currency);
            }
            String failure = obj.path("failure_reason").asText(null);
            WebhookKind kind = switch (type) {
                case "payment_intent.succeeded", "checkout.session.completed" -> WebhookKind.TOPUP_SUCCEEDED;
                case "payment_intent.payment_failed", "checkout.session.async_payment_failed" -> WebhookKind.TOPUP_FAILED;
                case "charge.refunded", "payment_intent.refunded" -> WebhookKind.TOPUP_REFUNDED;
                default -> WebhookKind.UNKNOWN;
            };
            return Optional.of(new ParsedWebhook(kind, id, amount, failure));
        } catch (Exception ex) {
            log.warn("[STUB STRIPE] parseWebhook error: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private static String hexHmacSha256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
        byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(raw.length * 2);
        for (byte b : raw) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
