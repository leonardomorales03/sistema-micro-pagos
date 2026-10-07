package com.micropay.backend.infrastructure.persistence.jpa.repositories;

import com.micropay.backend.infrastructure.persistence.jpa.entities.TierLimitJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TierLimitJpaRepository extends JpaRepository<TierLimitJpa, UUID> {
    Optional<TierLimitJpa> findByKycLevelAndCountryAndCurrency(
            TierLimitJpa.KycLevelEnum kycLevel, String country, String currency);
}
