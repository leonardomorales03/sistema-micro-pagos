package com.micropay.backend.infrastructure.reports;

import com.micropay.backend.domain.entities.ReconciliationMismatch;
import com.micropay.backend.domain.entities.ReconciliationRun;
import com.micropay.backend.domain.ports.out.ReconciliationRepository;
import com.micropay.backend.infrastructure.persistence.jpa.entities.ReconciliationMismatchJpa;
import com.micropay.backend.infrastructure.persistence.jpa.entities.ReconciliationRunJpa;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.ReconciliationMismatchJpaRepository;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.ReconciliationRunJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador Postgres del puerto {@link ReconciliationRepository}
 * (tablas {@code reconciliation_runs} y {@code reconciliation_mismatches}, V11).
 */
@Repository
public class PostgresReconciliationRepository implements ReconciliationRepository {

    private final ReconciliationRunJpaRepository runs;
    private final ReconciliationMismatchJpaRepository mismatches;

    public PostgresReconciliationRepository(ReconciliationRunJpaRepository runs,
                                            ReconciliationMismatchJpaRepository mismatches) {
        this.runs = runs;
        this.mismatches = mismatches;
    }

    @Override
    public ReconciliationRun saveRun(ReconciliationRun run) {
        return toRun(runs.save(toRunJpa(run)));
    }

    @Override
    public Optional<ReconciliationRun> findRunById(UUID id) {
        return runs.findById(id).map(this::toRun);
    }

    @Override
    public List<ReconciliationRun> listRuns(int page, int pageSize) {
        int p = Math.max(0, page);
        int size = Math.min(200, Math.max(1, pageSize));
        return runs.findAllByOrderByCreatedAtDesc(PageRequest.of(p, size))
                .stream()
                .map(this::toRun)
                .toList();
    }

    @Override
    public ReconciliationMismatch saveMismatch(ReconciliationMismatch mismatch) {
        return toMismatch(mismatches.save(toMismatchJpa(mismatch)));
    }

    @Override
    public List<ReconciliationMismatch> listMismatches(UUID runId) {
        return mismatches.findByRunIdOrderByCreatedAtAsc(runId)
                .stream()
                .map(this::toMismatch)
                .toList();
    }

    @Override
    public Optional<ReconciliationMismatch> findMismatchById(UUID id) {
        return mismatches.findById(id).map(this::toMismatch);
    }

    @Override
    public ReconciliationMismatch updateMismatch(ReconciliationMismatch mismatch) {
        return toMismatch(mismatches.save(toMismatchJpa(mismatch)));
    }

    // ──────────────────────── Mappers Domain ↔ JPA ────────────────────────

    private ReconciliationRunJpa toRunJpa(ReconciliationRun d) {
        ReconciliationRunJpa j = new ReconciliationRunJpa();
        j.setId(d.id());
        j.setTriggeredBy(d.triggeredBy());
        j.setProvider(d.provider());
        j.setStatus(d.status());
        j.setPeriodStart(d.periodStart());
        j.setPeriodEnd(d.periodEnd());
        j.setInternalCount(d.internalCount());
        j.setProviderCount(d.providerCount());
        j.setMismatchCount(d.mismatchCount());
        j.setNotes(d.notes());
        j.setCreatedAt(d.createdAt() != null ? d.createdAt() : Instant.now());
        j.setCompletedAt(d.completedAt());
        return j;
    }

    private ReconciliationRun toRun(ReconciliationRunJpa j) {
        if (j == null) return null;
        return new ReconciliationRun(
                j.getId(),
                j.getTriggeredBy(),
                j.getProvider(),
                j.getStatus(),
                j.getPeriodStart(),
                j.getPeriodEnd(),
                j.getInternalCount(),
                j.getProviderCount(),
                j.getMismatchCount(),
                j.getNotes(),
                j.getCreatedAt(),
                j.getCompletedAt()
        );
    }

    private ReconciliationMismatchJpa toMismatchJpa(ReconciliationMismatch d) {
        ReconciliationMismatchJpa j = new ReconciliationMismatchJpa();
        j.setId(d.id());
        j.setRunId(d.runId());
        j.setKind(d.kind());
        j.setInternalTxId(d.internalTxId());
        j.setProviderRef(d.providerRef());
        j.setInternalAmount(d.internalAmount());
        j.setProviderAmount(d.providerAmount());
        j.setCurrency(d.currency());
        j.setStatus(d.status());
        j.setResolutionNote(d.resolutionNote());
        j.setResolvedBy(d.resolvedBy());
        Instant created = d.createdAt() != null ? d.createdAt() : Instant.now();
        j.setCreatedAt(created);
        j.setUpdatedAt(d.updatedAt() != null ? d.updatedAt() : created);
        return j;
    }

    private ReconciliationMismatch toMismatch(ReconciliationMismatchJpa j) {
        if (j == null) return null;
        return new ReconciliationMismatch(
                j.getId(),
                j.getRunId(),
                j.getKind(),
                j.getInternalTxId(),
                j.getProviderRef(),
                j.getInternalAmount(),
                j.getProviderAmount(),
                j.getCurrency(),
                j.getStatus(),
                j.getResolutionNote(),
                j.getResolvedBy(),
                j.getCreatedAt(),
                j.getUpdatedAt()
        );
    }
}
