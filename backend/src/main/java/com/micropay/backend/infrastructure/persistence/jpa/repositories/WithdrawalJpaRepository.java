package com.micropay.backend.infrastructure.persistence.jpa.repositories;

import com.micropay.backend.infrastructure.persistence.jpa.entities.WithdrawalJpa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WithdrawalJpaRepository extends JpaRepository<WithdrawalJpa, UUID> {

    Optional<WithdrawalJpa> findByProviderRef(String providerRef);

    Page<WithdrawalJpa> findByWalletIdOrderByRequestedAtDesc(UUID walletId, Pageable pageable);

    Page<WithdrawalJpa> findByUserIdOrderByRequestedAtDesc(UUID userId, Pageable pageable);
}
