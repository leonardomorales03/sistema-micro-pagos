package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.entities.WebhookDelivery;
import com.micropay.backend.domain.valueobjects.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto OUT — persistencia del log de intentos de entrega de webhooks (Requisito 25.4).
 */
public interface WebhookDeliveryRepository {

    WebhookDelivery save(WebhookDelivery delivery);

    Optional<WebhookDelivery> findById(UUID id);

    /** Historial de entregas del comercio, más recientes primero. */
    List<WebhookDelivery> listByMerchant(UserId merchantUserId, int page, int pageSize);

    /** Entregas en estado RETRYABLE_FAIL cuyo {@code next_retry_at} ya venció. */
    List<WebhookDelivery> findDueRetries(Instant now, int limit);
}
