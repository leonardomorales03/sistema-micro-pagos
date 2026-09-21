package com.micropay.backend.infrastructure.persistence.jpa.repositories;

import com.micropay.backend.infrastructure.persistence.jpa.entities.PaymentRequestJpa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRequestJpaRepository extends JpaRepository<PaymentRequestJpa, UUID> {

    Optional<PaymentRequestJpa> findByShortCode(String shortCode);

    Page<PaymentRequestJpa> findByWalletToIdOrderByCreatedAtDesc(UUID walletToId, Pageable pageable);

    List<PaymentRequestJpa> findByStatusAndExpiresAtBefore(String status, Instant before);
}
