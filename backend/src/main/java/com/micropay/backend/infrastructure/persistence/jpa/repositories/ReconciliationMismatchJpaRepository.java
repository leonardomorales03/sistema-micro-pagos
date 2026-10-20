package com.micropay.backend.infrastructure.persistence.jpa.repositories;

import com.micropay.backend.infrastructure.persistence.jpa.entities.ReconciliationMismatchJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReconciliationMismatchJpaRepository extends JpaRepository<ReconciliationMismatchJpa, UUID> {

    List<ReconciliationMismatchJpa> findByRunIdOrderByCreatedAtAsc(UUID runId);
}
