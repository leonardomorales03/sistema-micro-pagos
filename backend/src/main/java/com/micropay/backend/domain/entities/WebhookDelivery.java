package com.micropay.backend.domain.entities;

import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WebhookDeliveryStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * WebhookDelivery — intento de entrega de un evento a un comercio (Requisito 25.4 / T22).
 *
 * Es append-only en cuanto al {@code eventId} (idempotency key): todos los reintentos de un
 * mismo evento comparten {@code eventId} e incrementan {@code attemptNumber}. El {@code requestBody}
 * es el sobre JSON firmado que se envió (se fija en el primer intento y no cambia).
 */
public final class WebhookDelivery {

    private final UUID id;
    private final UUID eventId;
    private final String eventType;
    private final UserId merchantUserId;
    private int attemptNumber;
    private WebhookDeliveryStatus status;
    private Integer httpStatus;
    private final String requestBody;
    private String responseBody;
    private Instant nextRetryAt;
    private final Instant createdAt;
    private Instant deliveredAt;

    private WebhookDelivery(Builder b) {
        if (b.eventId == null) throw new IllegalArgumentException("eventId es requerido");
        if (b.eventType == null || b.eventType.isBlank()) {
            throw new IllegalArgumentException("eventType es requerido");
        }
        if (b.merchantUserId == null) throw new IllegalArgumentException("merchantUserId es requerido");
        this.id = b.id == null ? UUID.randomUUID() : b.id;
        this.eventId = b.eventId;
        this.eventType = b.eventType;
        this.merchantUserId = b.merchantUserId;
        this.attemptNumber = b.attemptNumber == null || b.attemptNumber < 1 ? 1 : b.attemptNumber;
        this.status = b.status == null ? WebhookDeliveryStatus.QUEUED : b.status;
        this.httpStatus = b.httpStatus;
        this.requestBody = b.requestBody;
        this.responseBody = b.responseBody;
        this.nextRetryAt = b.nextRetryAt;
        this.createdAt = b.createdAt == null ? Instant.now() : b.createdAt;
        this.deliveredAt = b.deliveredAt;
    }

    public static Builder builder() { return new Builder(); }

    /** Nuevo intento en cola (attempt 1). */
    public static WebhookDelivery queued(UUID eventId, String eventType, UserId merchantUserId, String requestBody) {
        return WebhookDelivery.builder()
                .eventId(eventId)
                .eventType(eventType)
                .merchantUserId(merchantUserId)
                .requestBody(requestBody)
                .status(WebhookDeliveryStatus.QUEUED)
                .attemptNumber(1)
                .build();
    }

    // ────────── Comportamiento ──────────

    public void markSucceeded(Integer httpStatus, String responseBody) {
        this.status = WebhookDeliveryStatus.SUCCEEDED;
        this.httpStatus = httpStatus;
        this.responseBody = truncate(responseBody);
        this.nextRetryAt = null;
        this.deliveredAt = Instant.now();
    }

    public void markRetryableFailure(Integer httpStatus, String responseBody, Instant nextRetryAt) {
        this.status = WebhookDeliveryStatus.RETRYABLE_FAIL;
        this.httpStatus = httpStatus;
        this.responseBody = truncate(responseBody);
        this.nextRetryAt = nextRetryAt;
        this.deliveredAt = null;
    }

    public void markFailed(Integer httpStatus, String responseBody) {
        this.status = WebhookDeliveryStatus.FAILED;
        this.httpStatus = httpStatus;
        this.responseBody = truncate(responseBody);
        this.nextRetryAt = null;
        this.deliveredAt = null;
    }

    /** Prepara un reintento manual/automático: nuevo intento en cola, sin retry programado. */
    public void prepareRetry() {
        this.attemptNumber += 1;
        this.status = WebhookDeliveryStatus.QUEUED;
        this.nextRetryAt = null;
        this.deliveredAt = null;
    }

    public boolean isTerminal() {
        return status == WebhookDeliveryStatus.SUCCEEDED || status == WebhookDeliveryStatus.FAILED;
    }

    // ────────── Getters ──────────

    public UUID id() { return id; }
    public UUID eventId() { return eventId; }
    public String eventType() { return eventType; }
    public UserId merchantUserId() { return merchantUserId; }
    public int attemptNumber() { return attemptNumber; }
    public WebhookDeliveryStatus status() { return status; }
    public Integer httpStatus() { return httpStatus; }
    public String requestBody() { return requestBody; }
    public String responseBody() { return responseBody; }
    public Instant nextRetryAt() { return nextRetryAt; }
    public Instant createdAt() { return createdAt; }
    public Instant deliveredAt() { return deliveredAt; }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 2000 ? s : s.substring(0, 2000);
    }

    // ────────── Builder ──────────

    public static final class Builder {
        private UUID id;
        private UUID eventId;
        private String eventType;
        private UserId merchantUserId;
        private Integer attemptNumber;
        private WebhookDeliveryStatus status;
        private Integer httpStatus;
        private String requestBody;
        private String responseBody;
        private Instant nextRetryAt;
        private Instant createdAt;
        private Instant deliveredAt;

        public Builder id(UUID v) { this.id = v; return this; }
        public Builder eventId(UUID v) { this.eventId = v; return this; }
        public Builder eventType(String v) { this.eventType = v; return this; }
        public Builder merchantUserId(UserId v) { this.merchantUserId = v; return this; }
        public Builder attemptNumber(Integer v) { this.attemptNumber = v; return this; }
        public Builder status(WebhookDeliveryStatus v) { this.status = v; return this; }
        public Builder httpStatus(Integer v) { this.httpStatus = v; return this; }
        public Builder requestBody(String v) { this.requestBody = v; return this; }
        public Builder responseBody(String v) { this.responseBody = v; return this; }
        public Builder nextRetryAt(Instant v) { this.nextRetryAt = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder deliveredAt(Instant v) { this.deliveredAt = v; return this; }

        public WebhookDelivery build() { return new WebhookDelivery(this); }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof WebhookDelivery other && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
