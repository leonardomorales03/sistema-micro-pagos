package com.micropay.backend.application.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.micropay.backend.domain.entities.MerchantWebhookConfig;
import com.micropay.backend.domain.entities.WebhookDelivery;
import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.ports.out.WebhookConfigRepository;
import com.micropay.backend.domain.ports.out.WebhookDeliveryPort;
import com.micropay.backend.domain.ports.out.WebhookDeliveryRepository;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WebhookEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Casos de uso de webhooks a comercios (Requisito 25.4 / T22).
 *
 * · CRUD de la configuración (endpoint + secreto de firma + máscara de eventos).
 * · {@link #dispatchTo} construye el sobre {@code {id,type,timestamp,entity,data}}, persiste el
 *   intento en `webhook_deliveries` y lo entrega firmado (HMAC-SHA256).
 * · {@link #processDueRetries} reintenta con backoff 1m/5m/15m/1h/6h/24h (Requisito 25.4).
 *
 * El servicio NO es transaccional a nivel de clase: la entrega HTTP nunca debe mantener abierta
 * una transacción de base de datos.
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SECRET_BYTES = 32;

    private final WebhookConfigRepository configs;
    private final WebhookDeliveryRepository deliveries;
    private final WebhookDeliveryPort deliveryPort;
    private final ObjectMapper json;

    @Value("${micropay.webhooks.batch-size:100}")
    private int batchSize;

    @Value("${micropay.webhooks.retry-delays:1m,5m,15m,1h,6h,24h}")
    private String retryDelaysRaw;

    private List<Duration> cachedRetryDelays;

    public WebhookService(WebhookConfigRepository configs,
                          WebhookDeliveryRepository deliveries,
                          WebhookDeliveryPort deliveryPort,
                          ObjectMapper json) {
        this.configs = configs;
        this.deliveries = deliveries;
        this.deliveryPort = deliveryPort;
        this.json = json;
    }

    // ────────────────── Configuración (Requisito 25.4) ──────────────────

    public Optional<MerchantWebhookConfig> getConfig(UserId merchantUserId) {
        return configs.findByMerchant(merchantUserId);
    }

    /** Crea o actualiza la configuración. Si no existe, genera un secreto de firma. */
    @Transactional(rollbackFor = Exception.class)
    public MerchantWebhookConfig upsertConfig(UserId merchantUserId,
                                              String endpointUrl,
                                              List<String> subscribedEvents,
                                              boolean rotateSecret) {
        MerchantWebhookConfig cfg = configs.findByMerchant(merchantUserId).orElse(null);
        if (cfg == null) {
            cfg = MerchantWebhookConfig.create(merchantUserId, endpointUrl, generateSecret(), subscribedEvents);
            log.info("[WEBHOOK] Config creada merchant={} url={} events={}",
                    merchantUserId, endpointUrl, cfg.subscribedEvents());
        } else {
            cfg.update(endpointUrl, subscribedEvents);
            if (rotateSecret) cfg.rotateSecret(generateSecret());
            cfg.activate();
            log.info("[WEBHOOK] Config actualizada merchant={} url={} events={} rotateSecret={}",
                    merchantUserId, endpointUrl, cfg.subscribedEvents(), rotateSecret);
        }
        return configs.save(cfg);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteConfig(UserId merchantUserId) {
        configs.findByMerchant(merchantUserId).ifPresent(cfg -> {
            configs.delete(cfg);
            log.info("[WEBHOOK] Config eliminada merchant={}", merchantUserId);
        });
    }

    // ────────────────── Historial + redeliver (Requisito 25.5) ──────────────────

    public List<WebhookDelivery> listDeliveries(UserId merchantUserId, int page, int pageSize) {
        return deliveries.listByMerchant(merchantUserId, page, pageSize);
    }

    /** Reenvía manualmente una entrega del comercio (nuevo intento inmediato). */
    @Transactional(rollbackFor = Exception.class)
    public WebhookDelivery redeliver(UserId merchantUserId, UUID deliveryId) {
        WebhookDelivery d = getOwned(merchantUserId, deliveryId);
        MerchantWebhookConfig cfg = configs.findByMerchant(merchantUserId)
                .orElseThrow(() -> new BusinessRuleViolationException("WEBHOOK_CONFIG_NOT_FOUND",
                        "El comercio %s no tiene configuración de webhook".formatted(merchantUserId)));
        d.prepareRetry();
        WebhookDelivery saved = deliveries.save(d);
        return attempt(saved, cfg.endpointUrl(), cfg.signingSecret());
    }

    /** Dispara un evento de prueba al endpoint del propio comercio (Requisito 25.5). */
    @Transactional(rollbackFor = Exception.class)
    public WebhookDelivery simulate(UserId merchantUserId, String eventType) {
        MerchantWebhookConfig cfg = configs.findByMerchant(merchantUserId)
                .orElseThrow(() -> new BusinessRuleViolationException("WEBHOOK_CONFIG_NOT_FOUND",
                        "El comercio %s no tiene configuración de webhook".formatted(merchantUserId)));
        String normalized = WebhookEventType.parse(eventType).name();
        UUID entityId = UUID.randomUUID();
        Map<String, Object> data = Map.of(
                "simulated", true,
                "message", "Evento de prueba disparado por el comercio",
                "merchant_user_id", merchantUserId.toString());

        UUID eventId = UUID.randomUUID();
        WebhookDelivery d = WebhookDelivery.queued(eventId, normalized, merchantUserId,
                envelope(eventId, normalized, "SIMULATION", entityId, data));
        WebhookDelivery saved = deliveries.save(d);
        return attempt(saved, cfg.endpointUrl(), cfg.signingSecret());
    }

    // ────────────────── Despacho de eventos ──────────────────

    /**
     * Publica un evento al webhook del comercio indicado.
     *
     * @param urlOverride URL puntual (feature de PaymentRequest, T19); si el comercio tiene
     *                    configuración activa se ignora y se usa la configurada (firmada).
     * @return la entrega persistida, o {@code null} si no hay destino/suscripción aplicable.
     */
    public WebhookDelivery dispatchTo(UserId merchantUserId,
                                      WebhookEventType type,
                                      UUID entityId,
                                      String entityType,
                                      Map<String, Object> data,
                                      String urlOverride) {
        if (merchantUserId == null || type == null) return null;
        String eventType = type.name();
        String body = envelope(UUID.randomUUID(), eventType, entityType, entityId, data);

        MerchantWebhookConfig cfg = configs.findByMerchant(merchantUserId).orElse(null);
        if (cfg != null && cfg.active() && cfg.isSubscribedTo(eventType)) {
            WebhookDelivery d = deliveries.save(
                    WebhookDelivery.queued(UUID.randomUUID(), eventType, merchantUserId, body));
            return attempt(d, cfg.endpointUrl(), cfg.signingSecret());
        }

        // Fallback legado (T19): URL por-request, sin firma ni reintentos.
        if (urlOverride != null && !urlOverride.isBlank()) {
            log.info("[WEBHOOK] Entrega legacy (sin config) event={} -> {}", eventType, urlOverride);
            deliveryPort.deliver(new WebhookDeliveryPort.DeliveryRequest(
                    urlOverride, null, UUID.randomUUID().toString(), eventType, body));
            return null;
        }
        return null;
    }

    /** Reintenta las entregas recuperables vencidas (invocado por el scheduler). */
    public int processDueRetries() {
        List<WebhookDelivery> due = deliveries.findDueRetries(Instant.now(), batchSize);
        int processed = 0;
        for (WebhookDelivery d : due) {
            try {
                MerchantWebhookConfig cfg = configs.findByMerchant(d.merchantUserId()).orElse(null);
                if (cfg == null || !cfg.active()) {
                    d.markFailed(null, "Configuración de webhook inexistente o inactiva");
                    deliveries.save(d);
                    continue;
                }
                attempt(d, cfg.endpointUrl(), cfg.signingSecret());
                processed++;
            } catch (Exception e) {
                log.warn("[WEBHOOK] Reintento falló delivery={} event={}: {}", d.id(), d.eventId(), e.getMessage());
            }
        }
        return processed;
    }

    // ────────────────── Internos ──────────────────

    /** Ejecuta un intento y persiste el resultado con el backoff correspondiente. */
    private WebhookDelivery attempt(WebhookDelivery d, String url, String secret) {
        WebhookDeliveryPort.DeliveryResult r = deliveryPort.deliver(new WebhookDeliveryPort.DeliveryRequest(
                url, secret, d.eventId().toString(), d.eventType(), d.requestBody()));

        if (r.success()) {
            d.markSucceeded(r.httpStatus(), r.responseBody());
        } else {
            List<Duration> delays = retryDelays();
            int attemptNumber = d.attemptNumber();
            boolean canRetry = r.retryable() && attemptNumber <= delays.size();
            if (canRetry) {
                d.markRetryableFailure(r.httpStatus(), r.responseBody(),
                        Instant.now().plus(delays.get(attemptNumber - 1)));
            } else {
                d.markFailed(r.httpStatus(), r.responseBody());
            }
        }
        return deliveries.save(d);
    }

    private WebhookDelivery getOwned(UserId merchantUserId, UUID deliveryId) {
        WebhookDelivery d = deliveries.findById(deliveryId)
                .orElseThrow(() -> new BusinessRuleViolationException("WEBHOOK_DELIVERY_NOT_FOUND",
                        "Entrega " + deliveryId + " no encontrada"));
        if (!d.merchantUserId().equals(merchantUserId)) {
            throw new BusinessRuleViolationException("WEBHOOK_DELIVERY_NOT_OWNED_BY_MERCHANT",
                    "La entrega %s no pertenece al comercio %s".formatted(deliveryId, merchantUserId));
        }
        return d;
    }

    /** Sobre estándar: {@code {id, type, timestamp, entity:{type,id}, data}} (Requisito 25.4). */
    private String envelope(UUID eventId, String eventType, String entityType, UUID entityId, Map<String, Object> data) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("id", eventId.toString());
        env.put("type", eventType);
        env.put("timestamp", Instant.now().getEpochSecond());
        env.put("entity", Map.of(
                "type", entityType == null ? "UNKNOWN" : entityType,
                "id", entityId == null ? "" : entityId.toString()));
        env.put("data", data == null ? Map.of() : data);
        try {
            return json.writeValueAsString(env);
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible serializar el sobre del webhook: " + e.getMessage(), e);
        }
    }

    private List<Duration> retryDelays() {
        if (cachedRetryDelays == null) {
            cachedRetryDelays = Arrays.stream(retryDelaysRaw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(DurationStyle::detectAndParse)
                    .toList();
        }
        return cachedRetryDelays;
    }

    private static String generateSecret() {
        byte[] buf = new byte[SECRET_BYTES];
        RANDOM.nextBytes(buf);
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
