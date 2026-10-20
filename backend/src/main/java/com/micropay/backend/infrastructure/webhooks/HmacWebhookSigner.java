package com.micropay.backend.infrastructure.webhooks;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Firma HMAC-SHA256 de webhooks salientes (Requisito 25.4).
 *
 * Esquema compatible con Stripe: se firma la cadena {@code "<timestamp>.<body>"} y el header
 * resultante es {@code X-Signature: t=<timestamp>,v1=<hmac-hex>}. Incluir el timestamp en la firma
 * protege contra replay attacks (el receptor debe validar la ventana temporal).
 */
@Component
public class HmacWebhookSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String HEADER_PREFIX = "t=";
    private static final String VERSION = "v1";

    /** Devuelve el header completo {@code t=<ts>,v1=<hmac>}. */
    public String signatureHeader(String secret, long timestampSeconds, String body) {
        return HEADER_PREFIX + timestampSeconds + "," + VERSION + "="
                + HexFormat.of().formatHex(hmac(secret, signedPayload(timestampSeconds, body)));
    }

    /**
     * Verifica un header {@code t=<ts>,v1=<hmac>} contra el cuerpo recibido en comparación de tiempo constante.
     * Público para que los comercios puedan portarlo y para los property tests (Property 32).
     */
    public boolean verify(String secret, String signatureHeader, String body) {
        if (secret == null || signatureHeader == null || body == null) return false;
        Long ts = null;
        String provided = null;
        for (String part : signatureHeader.split(",")) {
            String p = part.trim();
            if (p.startsWith(HEADER_PREFIX)) {
                try {
                    ts = Long.parseLong(p.substring(HEADER_PREFIX.length()));
                } catch (NumberFormatException ignored) {
                    return false;
                }
            } else if (p.startsWith(VERSION + "=")) {
                provided = p.substring(VERSION.length() + 1);
            }
        }
        if (ts == null || provided == null || provided.isEmpty()) return false;

        String expected = HexFormat.of().formatHex(hmac(secret, signedPayload(ts, body)));
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    private static String signedPayload(long timestampSeconds, String body) {
        return timestampSeconds + "." + (body == null ? "" : body);
    }

    private static byte[] hmac(String secret, String payload) {
        if (secret == null) throw new IllegalArgumentException("signingSecret es requerido para firmar");
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible calcular HMAC-SHA256: " + e.getMessage(), e);
        }
    }
}
