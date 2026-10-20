package com.micropay.backend.domain.entities;

import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.domain.valueobjects.WebhookEventType;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * MerchantWebhookConfig — configuración de webhook saliente de un comercio
 * (Requisito 25.4 / T22). Única por usuario ({@code merchant_user_id} UNIQUE).
 *
 * INVARIANTES:
 *   BR#01: endpointUrl obligatoria y con esquema http(s).
 *   BR#02: signingSecret obligatorio (HMAC-SHA256 del cuerpo).
 *   BR#03: subscribedEvents nunca vacío; por defecto {@code ["*"]}.
 */
public final class MerchantWebhookConfig {

    private final UUID id;
    private final UserId merchantUserId;
    private String endpointUrl;
    private String signingSecret;
    private List<String> subscribedEvents;
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;

    private MerchantWebhookConfig(Builder b) {
        if (b.merchantUserId == null) throw new IllegalArgumentException("merchantUserId es requerido");
        this.id = b.id == null ? UUID.randomUUID() : b.id;
        this.merchantUserId = b.merchantUserId;
        this.endpointUrl = validateUrl(b.endpointUrl);
        if (b.signingSecret == null || b.signingSecret.isBlank()) {
            throw new IllegalArgumentException("signingSecret es requerido");
        }
        this.signingSecret = b.signingSecret;
        this.subscribedEvents = normalizeEvents(b.subscribedEvents);
        this.active = b.active == null || b.active;
        this.createdAt = b.createdAt == null ? Instant.now() : b.createdAt;
        this.updatedAt = b.updatedAt == null ? this.createdAt : b.updatedAt;
    }

    public static Builder builder() { return new Builder(); }

    /** Crea una configuración activa con la lista de eventos indicada (por defecto todos). */
    public static MerchantWebhookConfig create(UserId merchantUserId,
                                               String endpointUrl,
                                               String signingSecret,
                                               List<String> subscribedEvents) {
        return MerchantWebhookConfig.builder()
                .merchantUserId(merchantUserId)
                .endpointUrl(endpointUrl)
                .signingSecret(signingSecret)
                .subscribedEvents(subscribedEvents)
                .active(true)
                .build();
    }

    // ────────── Comportamiento ──────────

    /** Actualiza endpoint y máscara de eventos (la rotación de secreto es explícita). */
    public void update(String endpointUrl, List<String> subscribedEvents) {
        this.endpointUrl = validateUrl(endpointUrl);
        this.subscribedEvents = normalizeEvents(subscribedEvents);
        this.updatedAt = Instant.now();
    }

    /** Rota el secreto de firma (invalida la firma de entregas anteriores). */
    public void rotateSecret(String newSecret) {
        if (newSecret == null || newSecret.isBlank()) {
            throw new IllegalArgumentException("signingSecret es requerido");
        }
        this.signingSecret = newSecret;
        this.updatedAt = Instant.now();
    }

    public void activate()   { this.active = true;  this.updatedAt = Instant.now(); }
    public void deactivate() { this.active = false; this.updatedAt = Instant.now(); }

    /** ¿La máscara del comercio cubre este tipo de evento? */
    public boolean isSubscribedTo(String eventType) {
        return WebhookEventType.covers(subscribedEvents, eventType);
    }

    // ────────── Getters ──────────

    public UUID id() { return id; }
    public UserId merchantUserId() { return merchantUserId; }
    public String endpointUrl() { return endpointUrl; }
    public String signingSecret() { return signingSecret; }
    public List<String> subscribedEvents() { return subscribedEvents; }
    public boolean active() { return active; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    // ────────── Validaciones ──────────

    private static String validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("endpointUrl es requerida");
        }
        String v = url.trim();
        if (!v.startsWith("http://") && !v.startsWith("https://")) {
            throw new IllegalArgumentException("endpointUrl debe iniciar con http:// o https://: " + v);
        }
        return v;
    }

    private static List<String> normalizeEvents(List<String> events) {
        if (events == null || events.isEmpty()) return List.of(WebhookEventType.WILDCARD);
        List<String> cleaned = events.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toUpperCase)
                .distinct()
                .toList();
        return cleaned.isEmpty() ? List.of(WebhookEventType.WILDCARD) : cleaned;
    }

    // ────────── Builder ──────────

    public static final class Builder {
        private UUID id;
        private UserId merchantUserId;
        private String endpointUrl;
        private String signingSecret;
        private List<String> subscribedEvents;
        private Boolean active;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(UUID v) { this.id = v; return this; }
        public Builder merchantUserId(UserId v) { this.merchantUserId = v; return this; }
        public Builder endpointUrl(String v) { this.endpointUrl = v; return this; }
        public Builder signingSecret(String v) { this.signingSecret = v; return this; }
        public Builder subscribedEvents(List<String> v) { this.subscribedEvents = v; return this; }
        public Builder active(Boolean v) { this.active = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v) { this.updatedAt = v; return this; }

        public MerchantWebhookConfig build() { return new MerchantWebhookConfig(this); }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MerchantWebhookConfig other && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
