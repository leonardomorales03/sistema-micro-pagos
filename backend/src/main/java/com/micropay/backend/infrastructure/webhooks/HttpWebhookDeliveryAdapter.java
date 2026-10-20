package com.micropay.backend.infrastructure.webhooks;

import com.micropay.backend.domain.ports.out.WebhookDeliveryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * Adaptador HTTP de {@link WebhookDeliveryPort} (Requisito 25.4) — T22.
 *
 * · Firma el cuerpo con {@link HmacWebhookSigner} y lo envía en {@code X-Signature}.
 * · Clasifica el resultado: 2xx → éxito; 5xx/408/429/timeout/red → recuperable; resto → definitivo.
 * · Nunca propaga excepciones: siempre devuelve un {@link DeliveryResult}.
 */
@Component
public class HttpWebhookDeliveryAdapter implements WebhookDeliveryPort {

    private static final Logger log = LoggerFactory.getLogger(HttpWebhookDeliveryAdapter.class);
    private static final int MAX_RESPONSE_CHARS = 2000;
    private static final String USER_AGENT = "MicroPay-Webhooks/1.0";

    private final HmacWebhookSigner signer;
    private final HttpClient http;
    private final Duration timeout;

    public HttpWebhookDeliveryAdapter(HmacWebhookSigner signer,
                                      @Value("${micropay.webhooks.timeout-ms:5000}") long timeoutMs) {
        this.signer = signer;
        this.timeout = Duration.ofMillis(Math.max(500, timeoutMs));
        this.http = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public DeliveryResult deliver(DeliveryRequest request) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(request.url()))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .header("X-Event-Id", request.eventId())
                    .header("X-Event-Type", request.eventType())
                    .POST(HttpRequest.BodyPublishers.ofString(request.body()));

            if (request.signingSecret() != null && !request.signingSecret().isBlank()) {
                builder.header("X-Signature",
                        signer.signatureHeader(request.signingSecret(), Instant.now().getEpochSecond(), request.body()));
            }

            HttpResponse<String> resp = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();
            String body = truncate(resp.body());

            if (code >= 200 && code < 300) {
                log.info("[WEBHOOK] ✅ {} event={} -> {} status={}", request.eventType(), request.eventId(), request.url(), code);
                return new DeliveryResult(true, false, code, body);
            }

            boolean retryable = code >= 500 || code == 408 || code == 429;
            log.warn("[WEBHOOK] ⚠ {} event={} -> {} status={} retryable={}",
                    request.eventType(), request.eventId(), request.url(), code, retryable);
            return new DeliveryResult(false, retryable, code, body);
        } catch (Exception e) {
            log.warn("[WEBHOOK] ✖ {} event={} -> {} falló: {}",
                    request.eventType(), request.eventId(), request.url(), e.getMessage());
            return new DeliveryResult(false, true, null, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= MAX_RESPONSE_CHARS ? s : s.substring(0, MAX_RESPONSE_CHARS);
    }
}
