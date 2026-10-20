package com.micropay.backend.infrastructure.persistence.postgres;

import com.micropay.backend.domain.entities.WebhookDelivery;
import com.micropay.backend.domain.ports.out.WebhookDeliveryRepository;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WebhookDeliveryStatus;
import com.micropay.backend.infrastructure.persistence.jpa.entities.WebhookDeliveryJpa;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.WebhookDeliveryJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador Postgres del puerto {@link WebhookDeliveryRepository} (tabla `webhook_deliveries`, V9).
 */
@Repository
public class PostgresWebhookDeliveryRepository implements WebhookDeliveryRepository {

    private final WebhookDeliveryJpaRepository jpa;

    public PostgresWebhookDeliveryRepository(WebhookDeliveryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public WebhookDelivery save(WebhookDelivery delivery) {
        return toDomain(jpa.save(toJpa(delivery)));
    }

    @Override
    public Optional<WebhookDelivery> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public List<WebhookDelivery> listByMerchant(UserId merchantUserId, int page, int pageSize) {
        Pageable p = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, pageSize)));
        Page<WebhookDeliveryJpa> rs = jpa.findByMerchantUserIdOrderByCreatedAtDesc(merchantUserId.uuid(), p);
        return rs.getContent().stream().map(this::toDomain).toList();
    }

    @Override
    public List<WebhookDelivery> findDueRetries(Instant now, int limit) {
        Pageable p = PageRequest.of(0, Math.max(1, limit));
        return jpa.findDue(WebhookDeliveryJpa.WebhookDeliveryStatusJpa.RETRYABLE_FAIL, now, p)
                .stream().map(this::toDomain).toList();
    }

    // ──────────────────────── Mappers Domain ↔ JPA ────────────────────────

    private WebhookDeliveryJpa toJpa(WebhookDelivery d) {
        WebhookDeliveryJpa j = new WebhookDeliveryJpa();
        j.setId(d.id());
        j.setEventId(d.eventId());
        j.setEventType(d.eventType());
        j.setMerchantUserId(d.merchantUserId().uuid());
        j.setAttemptNumber(d.attemptNumber());
        j.setStatus(WebhookDeliveryJpa.WebhookDeliveryStatusJpa.valueOf(d.status().name()));
        j.setHttpStatus(d.httpStatus());
        j.setRequestBody(d.requestBody());
        j.setResponseBody(d.responseBody());
        j.setNextRetryAt(d.nextRetryAt());
        j.setCreatedAt(d.createdAt());
        j.setDeliveredAt(d.deliveredAt());
        return j;
    }

    private WebhookDelivery toDomain(WebhookDeliveryJpa j) {
        if (j == null) return null;
        return WebhookDelivery.builder()
                .id(j.getId())
                .eventId(j.getEventId())
                .eventType(j.getEventType())
                .merchantUserId(UserId.from(j.getMerchantUserId()))
                .attemptNumber(j.getAttemptNumber())
                .status(WebhookDeliveryStatus.valueOf(j.getStatus().name()))
                .httpStatus(j.getHttpStatus())
                .requestBody(j.getRequestBody())
                .responseBody(j.getResponseBody())
                .nextRetryAt(j.getNextRetryAt())
                .createdAt(j.getCreatedAt())
                .deliveredAt(j.getDeliveredAt())
                .build();
    }
}
