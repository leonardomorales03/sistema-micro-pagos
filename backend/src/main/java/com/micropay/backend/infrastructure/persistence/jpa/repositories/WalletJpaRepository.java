package com.micropay.backend.infrastructure.persistence.jpa.repositories;

import com.micropay.backend.infrastructure.persistence.jpa.entities.WalletJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletJpaRepository extends JpaRepository<WalletJpa, UUID> {

    Optional<WalletJpa> findByUserIdAndCurrency(UUID userId, String currency);

    List<WalletJpa> findByUserId(UUID userId);
}
