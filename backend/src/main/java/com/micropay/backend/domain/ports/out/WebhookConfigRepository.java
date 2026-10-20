package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.entities.MerchantWebhookConfig;
import com.micropay.backend.domain.valueobjects.UserId;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto OUT — persistencia de la configuración de webhook de un comercio (Requisito 25.4).
 */
public interface WebhookConfigRepository {

    MerchantWebhookConfig save(MerchantWebhookConfig config);

    Optional<MerchantWebhookConfig> findById(UUID id);

    Optional<MerchantWebhookConfig> findByMerchant(UserId merchantUserId);

    void delete(MerchantWebhookConfig config);
}
