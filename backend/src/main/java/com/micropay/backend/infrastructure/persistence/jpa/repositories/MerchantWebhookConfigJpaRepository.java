package com.micropay.backend.infrastructure.persistence.jpa.repositories;

import com.micropay.backend.infrastructure.persistence.jpa.entities.MerchantWebhookConfigJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MerchantWebhookConfigJpaRepository extends JpaRepository<MerchantWebhookConfigJpa, UUID> {

    Optional<MerchantWebhookConfigJpa> findByMerchantUserId(UUID merchantUserId);
}
