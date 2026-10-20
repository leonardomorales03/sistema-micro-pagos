package com.micropay.backend.infrastructure.persistence.jpa.repositories;

import com.micropay.backend.infrastructure.persistence.jpa.entities.PlanJpa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlanJpaRepository extends JpaRepository<PlanJpa, UUID> {

    Page<PlanJpa> findByMerchantUserIdOrderByCreatedAtDesc(UUID merchantUserId, Pageable pageable);
}
