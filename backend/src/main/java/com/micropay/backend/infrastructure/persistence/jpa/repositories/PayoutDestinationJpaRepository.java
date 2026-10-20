package com.micropay.backend.infrastructure.persistence.jpa.repositories;

import com.micropay.backend.infrastructure.persistence.jpa.entities.PayoutDestinationJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayoutDestinationJpaRepository extends JpaRepository<PayoutDestinationJpa, UUID> {

    List<PayoutDestinationJpa> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<PayoutDestinationJpa> findByUserIdAndIsDefaultTrue(UUID userId);

    Optional<PayoutDestinationJpa> findByProviderTokenRef(String providerTokenRef);

    long countByUserId(UUID userId);
}
