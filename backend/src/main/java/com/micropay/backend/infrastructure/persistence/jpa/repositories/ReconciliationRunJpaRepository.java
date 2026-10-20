package com.micropay.backend.infrastructure.persistence.jpa.repositories;

import com.micropay.backend.infrastructure.persistence.jpa.entities.ReconciliationRunJpa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReconciliationRunJpaRepository extends JpaRepository<ReconciliationRunJpa, UUID> {

    Page<ReconciliationRunJpa> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
