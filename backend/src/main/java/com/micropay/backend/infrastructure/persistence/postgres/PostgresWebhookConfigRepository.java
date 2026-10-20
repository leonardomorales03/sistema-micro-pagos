package com.micropay.backend.infrastructure.persistence.postgres;

import com.micropay.backend.domain.entities.MerchantWebhookConfig;
import com.micropay.backend.domain.ports.out.WebhookConfigRepository;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.infrastructure.persistence.jpa.entities.MerchantWebhookConfigJpa;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.MerchantWebhookConfigJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador Postgres del puerto {@link WebhookConfigRepository} (tabla `merchant_webhook_configs`, V9).
 */
@Repository
public class PostgresWebhookConfigRepository implements WebhookConfigRepository {

    private final MerchantWebhookConfigJpaRepository jpa;

    public PostgresWebhookConfigRepository(MerchantWebhookConfigJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public MerchantWebhookConfig save(MerchantWebhookConfig config) {
        return toDomain(jpa.save(toJpa(config)));
    }

    @Override
    public Optional<MerchantWebhookConfig> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<MerchantWebhookConfig> findByMerchant(UserId merchantUserId) {
        return jpa.findByMerchantUserId(merchantUserId.uuid()).map(this::toDomain);
    }

    @Override
    public void delete(MerchantWebhookConfig config) {
        jpa.deleteById(config.id());
    }

    // ──────────────────────── Mappers Domain ↔ JPA ────────────────────────

    private MerchantWebhookConfigJpa toJpa(MerchantWebhookConfig c) {
        MerchantWebhookConfigJpa j = new MerchantWebhookConfigJpa();
        j.setId(c.id());
        j.setMerchantUserId(c.merchantUserId().uuid());
        j.setEndpointUrl(c.endpointUrl());
        j.setSigningSecret(c.signingSecret());
        j.setSubscribedEvents(c.subscribedEvents().toArray(String[]::new));
        j.setActive(c.active());
        j.setCreatedAt(c.createdAt());
        j.setUpdatedAt(c.updatedAt());
        return j;
    }

    private MerchantWebhookConfig toDomain(MerchantWebhookConfigJpa j) {
        if (j == null) return null;
        return MerchantWebhookConfig.builder()
                .id(j.getId())
                .merchantUserId(UserId.from(j.getMerchantUserId()))
                .endpointUrl(j.getEndpointUrl())
                .signingSecret(j.getSigningSecret())
                .subscribedEvents(toList(j.getSubscribedEvents()))
                .active(j.isActive())
                .createdAt(j.getCreatedAt())
                .updatedAt(j.getUpdatedAt())
                .build();
    }

    private List<String> toList(String[] arr) {
        return arr == null ? List.of() : Arrays.asList(arr);
    }
}
