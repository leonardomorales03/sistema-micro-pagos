package com.micropay.backend.infrastructure.persistence.jpa.repositories;

import com.micropay.backend.infrastructure.persistence.jpa.entities.RiskIncidentJpa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RiskIncidentJpaRepository extends JpaRepository<RiskIncidentJpa, UUID> {

    Page<RiskIncidentJpa> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<RiskIncidentJpa> findByStatusOrderByCreatedAtDesc(String status);
}
