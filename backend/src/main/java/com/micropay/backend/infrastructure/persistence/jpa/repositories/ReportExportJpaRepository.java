package com.micropay.backend.infrastructure.persistence.jpa.repositories;

import com.micropay.backend.infrastructure.persistence.jpa.entities.ReportExportJpa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReportExportJpaRepository extends JpaRepository<ReportExportJpa, UUID> {

    Page<ReportExportJpa> findByRequestedByOrderByCreatedAtDesc(UUID requestedBy, Pageable pageable);

    Page<ReportExportJpa> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<ReportExportJpa> findByDownloadToken(String downloadToken);

    Page<ReportExportJpa> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);
}
