package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.entities.ReconciliationMismatch;
import com.micropay.backend.domain.entities.ReconciliationRun;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port OUT — Repositorio de reconciliación (tablas {@code reconciliation_runs} y
 * {@code reconciliation_mismatches}, V11).
 * Implementado por Infrastructure Adapter → PostgresReconciliationRepository.
 */
public interface ReconciliationRepository {

    ReconciliationRun saveRun(ReconciliationRun run);

    Optional<ReconciliationRun> findRunById(UUID id);

    /** Runs paginados DESC por createdAt. */
    List<ReconciliationRun> listRuns(int page, int pageSize);

    ReconciliationMismatch saveMismatch(ReconciliationMismatch mismatch);

    List<ReconciliationMismatch> listMismatches(UUID runId);

    Optional<ReconciliationMismatch> findMismatchById(UUID id);

    ReconciliationMismatch updateMismatch(ReconciliationMismatch mismatch);
}
