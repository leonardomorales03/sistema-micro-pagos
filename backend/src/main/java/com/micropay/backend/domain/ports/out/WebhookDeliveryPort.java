package com.micropay.backend.domain.ports.out;

/**
 * Puerto OUT — entrega HTTP de webhooks salientes firmados (Requisito 25.4).
 *
 * El adaptador es responsable de la firma HMAC-SHA256 ({@code X-Signature: t=<ts>,v1=<hmac>}),
 * el timeout y la clasificación del resultado en recuperable / no recuperable.
 */
public interface WebhookDeliveryPort {

    /**
     * @param url           endpoint destino (http/https)
     * @param signingSecret secreto HMAC del comercio ({@code null} = entrega sin firma, sólo fallback legado)
     * @param eventId       idempotency key del evento
     * @param eventType     tipo de evento
     * @param body          cuerpo JSON ya serializado (sobre {@code {id,type,timestamp,entity,data}})
     */
    record DeliveryRequest(String url, String signingSecret, String eventId, String eventType, String body) {}

    /**
     * @param success      true si el comercio respondió 2xx
     * @param retryable    true si el fallo es transitorio (5xx, 408, 429, timeout, red)
     * @param httpStatus   código HTTP (null si no hubo respuesta)
     * @param responseBody cuerpo de la respuesta (truncado por el adaptador)
     */
    record DeliveryResult(boolean success, boolean retryable, Integer httpStatus, String responseBody) {}

    DeliveryResult deliver(DeliveryRequest request);
}
