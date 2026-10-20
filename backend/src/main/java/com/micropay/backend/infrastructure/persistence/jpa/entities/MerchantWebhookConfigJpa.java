package com.micropay.backend.infrastructure.persistence.jpa.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA de `merchant_webhook_configs` (V9, T22).
 * La máscara de eventos es una columna Postgres {@code TEXT[]}.
 */
@Entity
@Table(name = "merchant_webhook_configs")
public class MerchantWebhookConfigJpa {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "merchant_user_id", columnDefinition = "uuid", nullable = false)
    private UUID merchantUserId;

    @Column(name = "endpoint_url", length = 1024, nullable = false)
    private String endpointUrl;

    @Column(name = "signing_secret", length = 255, nullable = false)
    private String signingSecret;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "subscribed_events", columnDefinition = "text[]", nullable = false)
    private String[] subscribedEvents;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public MerchantWebhookConfigJpa() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getMerchantUserId() { return merchantUserId; }
    public void setMerchantUserId(UUID merchantUserId) { this.merchantUserId = merchantUserId; }

    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }

    public String getSigningSecret() { return signingSecret; }
    public void setSigningSecret(String signingSecret) { this.signingSecret = signingSecret; }

    public String[] getSubscribedEvents() { return subscribedEvents; }
    public void setSubscribedEvents(String[] subscribedEvents) { this.subscribedEvents = subscribedEvents; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
