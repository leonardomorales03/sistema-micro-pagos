package com.micropay.backend.domain.ports.in;

import com.micropay.backend.domain.entities.ReconciliationMismatch;
import com.micropay.backend.domain.entities.ReconciliationRun;
import com.micropay.backend.domain.entities.ReportExport;
import com.micropay.backend.domain.valueobjects.CashflowReport;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * UseCases IN — Reports, Exports CSV asíncronos + Reconciliación (T25 / Requisitos 28.x).
 * Implementado en Application Layer → ReportService.
 * <p>
 * Todas las acciones relevantes registran un {@code AuditEvent} (Property 31).
 */
public interface ReportUseCases {

    // ─────────────── Exports CSV ───────────────

    /** Encola una exportación CSV (estado QUEUED) para el usuario solicitante. */
    ReportExport requestExport(String type, Map<String, Object> filters, UUID requestedBy);

    /** Lista exportaciones del usuario; {@code requestedBy == null} → todas (solo admin). */
    List<ReportExport> listExports(UUID requestedBy, int page, int pageSize);

    /** Busca una exportación por su download_token (valida estado/expiración). */
    ReportExport getByToken(String token);

    // ─────────────── Cashflow diario ───────────────

    /** Informe diario de flujo de caja (acreditado/debitado/neto/fees por tipo). */
    CashflowReport cashflow(LocalDate date);

    // ─────────────── Reconciliación ───────────────

    ReconciliationRun runReconciliation(String provider, Instant periodStart, Instant periodEnd, UUID triggeredBy);

    List<ReconciliationRun> listRuns(int page, int pageSize);

    List<ReconciliationMismatch> listMismatches(UUID runId);

    ReconciliationMismatch resolveMismatch(UUID id, String status, String note, UUID resolvedBy);
}
