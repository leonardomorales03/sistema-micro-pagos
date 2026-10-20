package com.micropay.backend.domain.valueobjects;

/**
 * Estado de un intento de entrega de webhook a un comercio (Requisito 25.4).
 *
 * QUEUED          → Intentando entregar (o reintento programado en curso).
 * SUCCEEDED       → El comercio respondió 2xx.
 * RETRYABLE_FAIL  → Fallo transitorio (5xx/timeout/red); se reintentará según backoff.
 * FAILED          → Fallo definitivo (4xx no recuperable o reintentos agotados).
 */
public enum WebhookDeliveryStatus {
    QUEUED,
    SUCCEEDED,
    RETRYABLE_FAIL,
    FAILED
}
