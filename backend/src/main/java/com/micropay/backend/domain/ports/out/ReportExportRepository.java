package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.entities.ReportExport;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port OUT — Repositorio de exportaciones CSV (tabla {@code report_exports}, V11).
 * Implementado por Infrastructure Adapter → PostgresReportExportRepository.
 */
public interface ReportExportRepository {

    ReportExport save(ReportExport export);

    Optional<ReportExport> findById(UUID id);

    Optional<ReportExport> findByToken(String downloadToken);

    /** Lista exportaciones paginadas DESC por createdAt. Si {@code requestedBy} es null → todas. */
    List<ReportExport> list(UUID requestedBy, int page, int pageSize);

    /** Exportaciones pendientes (QUEUED) más antiguas primero — usado por el poller de jobs. */
    List<ReportExport> findQueued(int limit);
}
