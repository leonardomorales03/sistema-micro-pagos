package com.micropay.backend.infrastructure.persistence.jpa.repositories;

import com.micropay.backend.infrastructure.persistence.jpa.entities.TransactionJpa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionJpaRepository extends JpaRepository<TransactionJpa, UUID> {

    Optional<TransactionJpa> findByShortCode(String shortCode);

    Page<TransactionJpa> findByWalletFromIdOrWalletToIdOrderByCreatedAtDesc(UUID walletFromId, UUID walletToId, Pageable pageable);
}
