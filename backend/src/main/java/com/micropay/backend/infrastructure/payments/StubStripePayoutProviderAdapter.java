package com.micropay.backend.infrastructure.payments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micropay.backend.domain.ports.out.PayoutProviderPort;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.Money;
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
 * Sandbox / Stub del proveedor de payouts (retiros a cuenta bancaria).
 *
 * initiatePayout → providerRef `po_stub_<24>` + status "processing".
 * validateWebhook → Stripe-Signature style `t=,v1=` HMAC-SHA256.
 * parseWebhook → JSON {type: payout.completed|payout.failed|payout.returned, data.object.id, amount, currency, failure_reason}
 */
@Component
@Profile({"dev", "test", "sandbox"})
public class StubStripePayoutProviderAdapter implements PayoutProviderPort {

    private static final Logger log = LoggerFactory.getLogger(StubStripePayoutProviderAdapter.class);
    private static final String PREFIX_PAYOUT = "po_stub_";
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final String webhookSecret;
    private final ObjectMapper json;

    public StubStripePayoutProviderAdapter(
            @Value("${micropay.payments.stripe.webhook-secret:stripe-webhook-secret-dev-only}") String webhookSecret,
            ObjectMapper json) {
        this.webhookSecret = webhookSecret == null ? "stripe-webhook-secret-dev-only" : webhookSecret;
        this.json = json == null ? new ObjectMapper() : json;
    }

    @Override
    public PayoutInitResult initiatePayout(Money amount, String maskedAccountLabel, String idempotencyKey) {
        String ref = PREFIX_PAYOUT + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        log.info("[STUB STRIPE PAYOUT] iniciado ref={} amount={} account={}", ref, amount, maskedAccountLabel);
        return new PayoutInitResult(ref, "processing");
    }

    @Override
    public boolean validateWebhook(String payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            log.debug("[STUB STRIPE PAYOUT] Webhook sin firma, SKIP valido");
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
            if (!ok) log.warn("[STUB STRIPE PAYOUT] Webhook firma INVALIDA");
            return ok;
        } catch (Exception ex) {
            log.warn("[STUB STRIPE PAYOUT] Webhook excepción firma: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public Optional<ParsedPayoutEvent> parseWebhook(String payload) {
        if (payload == null || payload.isBlank()) return Optional.empty();
        try {
            JsonNode root = json.readTree(payload);
            String type = root.path("type").asText("");
            JsonNode obj = root.path("data").path("object");
            String id = obj.path("id").asText(null);
            if (id == null) id = root.path("provider_ref").asText(null);
            if (id == null) return Optional.empty();
            String currencyStr = obj.path("currency").asText("COP").toUpperCase();
            Currency currency;
            try { currency = Currency.valueOf(currencyStr); }
            catch (Exception e) { currency = Currency.COP; }
            Money amount;
            try {
                BigDecimal minor = obj.path("amount").decimalValue();
                int exponent = obj.has("currency_exponent") ? obj.path("currency_exponent").intValue() : 2;
                amount = Money.of(minor.movePointLeft(exponent), currency);
            } catch (Exception e) {
                amount = Money.of(BigDecimal.ZERO, currency);
            }
            String failure = obj.path("failure_reason").asText(null);
            PayoutEventKind kind = switch (type) {
                case "payout.completed", "payout.paid" -> PayoutEventKind.PAYOUT_COMPLETED;
                case "payout.failed" -> PayoutEventKind.PAYOUT_FAILED;
                case "payout.returned", "payout.reversed" -> PayoutEventKind.PAYOUT_RETURNED;
                default -> PayoutEventKind.UNKNOWN;
            };
            return Optional.of(new ParsedPayoutEvent(kind, id, amount, failure));
        } catch (Exception ex) {
            log.warn("[STUB STRIPE PAYOUT] parseWebhook error: {}", ex.getMessage());
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
