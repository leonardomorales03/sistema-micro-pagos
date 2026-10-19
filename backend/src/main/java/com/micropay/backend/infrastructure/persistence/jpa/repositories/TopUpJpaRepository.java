package com.micropay.backend.infrastructure.persistence.jpa.repositories;

import com.micropay.backend.infrastructure.persistence.jpa.entities.TopUpJpa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TopUpJpaRepository extends JpaRepository<TopUpJpa, UUID> {

    Optional<TopUpJpa> findByProviderRef(String providerRef);

    Page<TopUpJpa> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);

    Page<TopUpJpa> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
