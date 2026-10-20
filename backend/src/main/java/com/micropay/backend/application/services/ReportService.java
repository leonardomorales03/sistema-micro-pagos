package com.micropay.backend.application.services;

import com.micropay.backend.domain.entities.AuditEvent;
import com.micropay.backend.domain.entities.ReconciliationMismatch;
import com.micropay.backend.domain.entities.ReconciliationRun;
import com.micropay.backend.domain.entities.ReportExport;
import com.micropay.backend.domain.entities.Transaction;
import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.exceptions.ReconciliationMismatchNotFoundException;
import com.micropay.backend.domain.exceptions.ReportExportNotFoundException;
import com.micropay.backend.domain.ports.in.ReportUseCases;
import com.micropay.backend.domain.ports.out.AuditEventRepository;
import com.micropay.backend.domain.ports.out.NotificationAdapter;
import com.micropay.backend.domain.ports.out.ProviderReconciliationPort;
import com.micropay.backend.domain.ports.out.ReconciliationRepository;
import com.micropay.backend.domain.ports.out.ReportExportRepository;
import com.micropay.backend.domain.ports.out.ReportStoragePort;
import com.micropay.backend.domain.ports.out.TransactionRepository;
import com.micropay.backend.domain.valueobjects.AnnualTaxLine;
import com.micropay.backend.domain.valueobjects.CashflowReport;
import com.micropay.backend.domain.valueobjects.ExportType;
import com.micropay.backend.domain.valueobjects.ProviderEntry;
import com.micropay.backend.domain.valueobjects.TransactionStatus;
import com.micropay.backend.domain.valueobjects.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * ReportService (T25) — Reports, Exports CSV asíncronos + Reconciliación.
 * <p>
 * Los exports se encolan en estado QUEUED y son procesados de forma asíncrona por
 * {@code ReportExportJob} (poller programado) mediante {@link #processQueuedExports()}.
 * Cada acción relevante deja traza en {@code audit_events} (Property 31).
 */
@Service
public class ReportService implements ReportUseCases {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private static final int PAGE_SIZE = 200;
    private static final String ENTITY_REPORT_EXPORT = "REPORT_EXPORT";
    private static final String ENTITY_RECON_RUN = "RECONCILIATION_RUN";
    private static final String ENTITY_RECON_MISMATCH = "RECONCILIATION_MISMATCH";

    private final ReportExportRepository exports;
    private final ReconciliationRepository reconciliation;
    private final TransactionRepository transactions;
    private final ReportStoragePort storage;
    private final ProviderReconciliationPort providerPort;
    private final AuditEventRepository audit;
    private final NotificationAdapter notifications;

    @Value("${micropay.reports.download-ttl-hours:24}")
    private long downloadTtlHours;

    @Value("${micropay.reports.max-rows:50000}")
    private long maxRows;

    @Value("${micropay.reports.poll-batch-size:10}")
    private int pollBatchSize;

    public ReportService(ReportExportRepository exports,
                         ReconciliationRepository reconciliation,
                         TransactionRepository transactions,
                         ReportStoragePort storage,
                         ProviderReconciliationPort providerPort,
                         AuditEventRepository audit,
                         NotificationAdapter notifications) {
        this.exports = exports;
        this.reconciliation = reconciliation;
        this.transactions = transactions;
        this.storage = storage;
        this.providerPort = providerPort;
        this.audit = audit;
        this.notifications = notifications;
    }

    // ═════════════════════════ Exports CSV ═════════════════════════

    @Override
    @Transactional
    public ReportExport requestExport(String type, Map<String, Object> filters, UUID requestedBy) {
        if (!ExportType.isValid(type)) {
            throw new BusinessRuleViolationException("REPORT_EXPORT_TYPE_INVALID",
                    "Tipo de exportación no soportado: " + type
                            + ". Válidos: TX_HISTORY, TOPUPS, WITHDRAWALS, ANNUAL_TAX_SUMMARY");
        }
        if (requestedBy == null) {
            throw new BusinessRuleViolationException("REPORT_EXPORT_REQUESTER_REQUIRED",
                    "El solicitante del reporte es requerido");
        }
        Map<String, Object> safeFilters = filters != null ? filters : Map.of();
        ReportExport saved = exports.save(ReportExport.queued(requestedBy, type.trim().toUpperCase(), safeFilters));
        audit("REPORT_EXPORT_REQUESTED", ENTITY_REPORT_EXPORT, saved.id(), requestedBy,
                Map.of("type", saved.type(), "filters", safeFilters));
        log.info("[REPORT] export {} encolado type={} by={}", saved.id(), saved.type(), requestedBy);
        return saved;
    }

    @Override
    public List<ReportExport> listExports(UUID requestedBy, int page, int pageSize) {
        return exports.list(requestedBy, page, pageSize);
    }

    @Override
    public ReportExport getByToken(String token) {
        ReportExport e = exports.findByToken(token)
                .orElseThrow(() -> new ReportExportNotFoundException(token));
        if (!ReportExport.COMPLETED.equals(e.status())) {
            throw new BusinessRuleViolationException("REPORT_EXPORT_NOT_READY",
                    "La exportación no está lista (estado " + e.status() + ")");
        }
        if (e.expiresAt() != null && e.expiresAt().isBefore(Instant.now())) {
            throw new BusinessRuleViolationException("REPORT_EXPORT_EXPIRED",
                    "El enlace de descarga ha expirado");
        }
        return e;
    }

    /**
     * Procesa los exports encolados (queued). Invocado por {@code ReportExportJob}
     * (poller programado + ShedLock). Devuelve cuántos se procesaron.
     */
    public int processQueuedExports() {
        List<ReportExport> queued = exports.findQueued(Math.max(1, pollBatchSize));
        int processed = 0;
        for (ReportExport queuedExport : queued) {
            try {
                processOneExport(queuedExport);
                processed++;
            } catch (Exception ex) {
                log.error("[REPORT] fallo procesando export {}: {}", queuedExport.id(), ex.getMessage(), ex);
                try {
                    exports.save(queuedExport.failed(truncate(ex.getMessage(), 512)));
                    audit("REPORT_EXPORT_FAILED", ENTITY_REPORT_EXPORT, queuedExport.id(),
                            queuedExport.requestedBy(), Map.of("error", truncate(ex.getMessage(), 400)));
                } catch (Exception inner) {
                    log.warn("[REPORT] no se pudo marcar FAILED el export {}: {}",
                            queuedExport.id(), inner.getMessage());
                }
            }
        }
        return processed;
    }

    private void processOneExport(ReportExport queuedExport) {
        ReportExport running = exports.save(queuedExport.running(Instant.now()));
        CsvResult result = buildCsv(running);
        String fileName = "report_%s_%s.csv".formatted(
                running.type().toLowerCase(), running.id().toString().replace("-", ""));
        String path = storage.store(fileName, result.content());
        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Instant expiresAt = Instant.now().plus(Duration.ofHours(Math.max(1, downloadTtlHours)));

        ReportExport done = exports.save(running.completed(path, token, result.rowCount(), expiresAt));
        audit("REPORT_EXPORT_COMPLETED", ENTITY_REPORT_EXPORT, done.id(), done.requestedBy(),
                Map.of("type", done.type(), "rowCount", done.rowCount(), "filePath", path));
        notifyReady(done);
        log.info("[REPORT] export {} COMPLETED rows={} path={}", done.id(), done.rowCount(), path);
    }

    private void notifyReady(ReportExport e) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("exportId", e.id().toString());
            payload.put("type", e.type());
            payload.put("downloadToken", e.downloadToken());
            payload.put("expiresAt", String.valueOf(e.expiresAt()));
            payload.put("rowCount", e.rowCount());
            payload.put("downloadEndpoint", "/api/v1/admin/reports/exports/download/" + e.downloadToken());
            notifications.send(new NotificationAdapter.NotificationRequest(
                    UserId.from(e.requestedBy()),
                    NotificationAdapter.Channel.EMAIL,
                    "REPORT_EXPORT_READY",
                    payload));
        } catch (Exception ex) {
            // Tolerante a fallos: la generación ya está completada.
            log.warn("[REPORT] no se pudo notificar el export {}: {}", e.id(), ex.getMessage());
        }
    }

    // ═════════════════════════ Cashflow diario ═════════════════════════

    @Override
    public CashflowReport cashflow(LocalDate date) {
        LocalDate day = date != null ? date : LocalDate.now(ZoneOffset.UTC);
        Instant from = day.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        Map<String, BigDecimal> credited = new TreeMap<>();
        Map<String, BigDecimal> debited = new TreeMap<>();
        Map<String, BigDecimal> fees = new TreeMap<>();
        BigDecimal totalCredited = BigDecimal.ZERO;
        BigDecimal totalDebited = BigDecimal.ZERO;

        for (Transaction t : fetchTransactions(null, null, null, from, to)) {
            if (t.status() != TransactionStatus.COMPLETED) continue;
            String type = t.type().name();
            BigDecimal gross = t.grossAmount().amount();
            if (t.walletToId() != null) {
                credited.merge(type, gross, BigDecimal::add);
                totalCredited = totalCredited.add(gross);
            }
            if (t.walletFromId() != null) {
                debited.merge(type, gross, BigDecimal::add);
                totalDebited = totalDebited.add(gross);
            }
            fees.merge(type, t.feeAmount().amount(), BigDecimal::add);
        }

        BigDecimal net = totalCredited.subtract(totalDebited);
        audit("CASHFLOW_REPORT_VIEWED", "CASHFLOW_REPORT", null, null, Map.of("date", day.toString()));
        return new CashflowReport(day, credited, debited, net, fees);
    }

    // ═════════════════════════ Reconciliación ═════════════════════════

    @Override
    @Transactional
    public ReconciliationRun runReconciliation(String provider, Instant periodStart, Instant periodEnd,
                                               UUID triggeredBy) {
        if (provider == null || provider.isBlank()) {
            throw new BusinessRuleViolationException("RECONCILIATION_PROVIDER_REQUIRED",
                    "El proveedor es requerido");
        }
        ReconciliationRun run = reconciliation.saveRun(
                ReconciliationRun.running(triggeredBy, provider.trim().toLowerCase(), periodStart, periodEnd));
        try {
            List<ProviderEntry> providerEntries = providerPort.fetchEntries(provider, periodStart, periodEnd);
            Map<String, ProviderEntry> byRef = new LinkedHashMap<>();
            for (ProviderEntry pe : providerEntries) {
                if (pe.providerRef() != null && !pe.providerRef().isBlank()) {
                    byRef.putIfAbsent(pe.providerRef(), pe);
                }
            }

            List<Transaction> internal = new ArrayList<>();
            for (Transaction t : fetchTransactions(null, null, null, periodStart, periodEnd)) {
                if (t.status() == TransactionStatus.COMPLETED && t.externalRef() != null
                        && !t.externalRef().isBlank()) {
                    internal.add(t);
                }
            }
            Set<String> internalRefs = new HashSet<>();
            for (Transaction t : internal) internalRefs.add(t.externalRef());

            int mismatches = 0;

            // Internas que faltan o con importe distinto en el proveedor.
            for (Transaction t : internal) {
                ProviderEntry pe = byRef.get(t.externalRef());
                if (pe == null) {
                    reconciliation.saveMismatch(ReconciliationMismatch.create(
                            run.id(), ReconciliationMismatch.KIND_MISSING_AT_PROVIDER, t.id().uuid(),
                            t.externalRef(), t.grossAmount().amount(), null,
                            t.grossAmount().currency().isoCode()));
                    mismatches++;
                } else if (pe.amount() == null || t.grossAmount().amount().compareTo(pe.amount()) != 0) {
                    reconciliation.saveMismatch(ReconciliationMismatch.create(
                            run.id(), ReconciliationMismatch.KIND_AMOUNT_MISMATCH, t.id().uuid(),
                            t.externalRef(), t.grossAmount().amount(), pe.amount(),
                            t.grossAmount().currency().isoCode()));
                    mismatches++;
                }
            }

            // Entradas del proveedor sin correspondencia interna.
            for (ProviderEntry pe : providerEntries) {
                if (pe.providerRef() == null || pe.providerRef().isBlank()) continue;
                if (!internalRefs.contains(pe.providerRef())) {
                    reconciliation.saveMismatch(ReconciliationMismatch.create(
                            run.id(), ReconciliationMismatch.KIND_UNEXPECTED_PROVIDER_ENTRY, null,
                            pe.providerRef(), null, pe.amount(), pe.currency()));
                    mismatches++;
                }
            }

            ReconciliationRun completed = reconciliation.saveRun(
                    run.completed(internal.size(), providerEntries.size(), mismatches));
            audit("RECONCILIATION_RUN_COMPLETED", ENTITY_RECON_RUN, completed.id(), triggeredBy,
                    Map.of("provider", completed.provider(),
                            "internalCount", completed.internalCount(),
                            "providerCount", completed.providerCount(),
                            "mismatchCount", completed.mismatchCount()));
            log.info("[RECONCILIATION] run {} provider={} internal={} provider={} mismatches={}",
                    completed.id(), completed.provider(), completed.internalCount(),
                    completed.providerCount(), completed.mismatchCount());
            return completed;
        } catch (RuntimeException ex) {
            reconciliation.saveRun(run.failed(truncate(ex.getMessage(), 512)));
            audit("RECONCILIATION_RUN_FAILED", ENTITY_RECON_RUN, run.id(), triggeredBy,
                    Map.of("provider", run.provider(), "error", truncate(ex.getMessage(), 400)));
            throw ex;
        }
    }

    @Override
    public List<ReconciliationRun> listRuns(int page, int pageSize) {
        return reconciliation.listRuns(page, pageSize);
    }

    @Override
    public List<ReconciliationMismatch> listMismatches(UUID runId) {
        return reconciliation.listMismatches(runId);
    }

    @Override
    @Transactional
    public ReconciliationMismatch resolveMismatch(UUID id, String status, String note, UUID resolvedBy) {
        ReconciliationMismatch m = reconciliation.findMismatchById(id)
                .orElseThrow(() -> new ReconciliationMismatchNotFoundException(id));
        String target = status == null ? "" : status.trim().toUpperCase();
        if (!ReconciliationMismatch.INVESTIGATING.equals(target)
                && !ReconciliationMismatch.RESOLVED.equals(target)) {
            throw new BusinessRuleViolationException("MISMATCH_STATUS_INVALID",
                    "status debe ser INVESTIGATING o RESOLVED");
        }
        if (ReconciliationMismatch.RESOLVED.equals(target) && (note == null || note.isBlank())) {
            throw new BusinessRuleViolationException("MISMATCH_RESOLUTION_NOTE_REQUIRED",
                    "Se requiere 'note' para marcar una discrepancia como RESOLVED");
        }
        ReconciliationMismatch updated = new ReconciliationMismatch(
                m.id(), m.runId(), m.kind(), m.internalTxId(), m.providerRef(),
                m.internalAmount(), m.providerAmount(), m.currency(), target,
                note != null ? note : m.resolutionNote(),
                ReconciliationMismatch.RESOLVED.equals(target) ? resolvedBy : m.resolvedBy(),
                m.createdAt(), Instant.now());
        ReconciliationMismatch saved = reconciliation.updateMismatch(updated);
        audit(ReconciliationMismatch.RESOLVED.equals(target)
                        ? "RECONCILIATION_MISMATCH_RESOLVED" : "RECONCILIATION_MISMATCH_INVESTIGATING",
                ENTITY_RECON_MISMATCH, saved.id(), resolvedBy,
                Map.of("status", saved.status(), "runId", saved.runId().toString()));
        return saved;
    }

    // ═════════════════════════ Generación de CSV ═════════════════════════

    private record CsvResult(byte[] content, long rowCount) {}

    private CsvResult buildCsv(ReportExport e) {
        ExportType type = ExportType.from(e.type());
        Map<String, Object> filters = e.filters() != null ? e.filters() : Map.of();
        StringBuilder sb = new StringBuilder();
        appendWatermark(sb, e, filters);

        long rows;
        switch (type) {
            case TX_HISTORY -> rows = csvTransactions(sb, null, filters);
            case TOPUPS -> rows = csvTransactions(sb, "DEPOSIT_TOPUP", filters);
            case WITHDRAWALS -> rows = csvTransactions(sb, "WITHDRAW", filters);
            case ANNUAL_TAX_SUMMARY -> rows = csvAnnualTaxSummary(sb, filters);
            default -> throw new BusinessRuleViolationException("REPORT_EXPORT_TYPE_INVALID",
                    "Tipo de exportación no soportado: " + type);
        }
        return new CsvResult(sb.toString().getBytes(StandardCharsets.UTF_8), rows);
    }

    /** Cabecera de watermark/metadatos del reporte (líneas comentadas con '#'). */
    private void appendWatermark(StringBuilder sb, ReportExport e, Map<String, Object> filters) {
        sb.append("# export_type=").append(e.type()).append('\n');
        sb.append("# export_id=").append(e.id()).append('\n');
        sb.append("# exported_by=").append(e.requestedBy()).append('\n');
        sb.append("# exported_at=").append(Instant.now()).append('\n');
        sb.append("# filters=").append(toJsonInline(filters)).append('\n');
        sb.append('\n');
    }

    private long csvTransactions(StringBuilder sb, String forcedType, Map<String, Object> filters) {
        Instant from = parseInstant(filters.get("from"));
        Instant to = parseInstant(filters.get("to"));
        String status = str(filters.get("status"));
        String currency = str(filters.get("currency"));
        String type = forcedType != null ? forcedType : str(filters.get("type"));

        sb.append("tx_id,type,short_code,status,gross_amount,fee_amount,net_amount,currency,")
          .append("wallet_from_id,wallet_to_id,created_by,created_at,note\n");

        long rows = 0;
        for (Transaction t : fetchTransactions(type, status, currency, from, to)) {
            sb.append(csv(t.id().uuid())).append(',')
              .append(csv(t.type().name())).append(',')
              .append(csv(t.shortCode() != null ? t.shortCode().value() : null)).append(',')
              .append(csv(t.status().name())).append(',')
              .append(t.grossAmount().amount()).append(',')
              .append(t.feeAmount().amount()).append(',')
              .append(t.netAmount().amount()).append(',')
              .append(csv(t.grossAmount().currency().isoCode())).append(',')
              .append(csv(t.walletFromId() != null ? t.walletFromId().uuid() : null)).append(',')
              .append(csv(t.walletToId() != null ? t.walletToId().uuid() : null)).append(',')
              .append(csv(t.createdBy())).append(',')
              .append(csv(t.createdAt())).append(',')
              .append(csv(t.note()))
              .append('\n');
            rows++;
        }
        return rows;
    }

    private long csvAnnualTaxSummary(StringBuilder sb, Map<String, Object> filters) {
        int year = parseYear(filters.get("year"));
        Instant from = LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = LocalDate.of(year + 1, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();

        sb.append("user_id,type,currency,total_gross,total_fee,total_net,count\n");
        long rows = 0;
        for (AnnualTaxLine line : transactions.annualTaxSummary(from, to)) {
            sb.append(csv(line.userId())).append(',')
              .append(csv(line.type())).append(',')
              .append(csv(line.currency())).append(',')
              .append(line.totalGross()).append(',')
              .append(line.totalFee()).append(',')
              .append(line.totalNet()).append(',')
              .append(line.count())
              .append('\n');
            rows++;
        }
        return rows;
    }

    // ═════════════════════════ Helpers ═════════════════════════

    /** Recorre {@link TransactionRepository#search} paginando hasta agotar o alcanzar maxRows. */
    private List<Transaction> fetchTransactions(String type, String status, String currency,
                                                Instant from, Instant to) {
        List<Transaction> all = new ArrayList<>();
        int page = 0;
        while (all.size() < maxRows) {
            List<Transaction> batch = transactions.search(null, type, status, currency, from, to, page, PAGE_SIZE);
            if (batch.isEmpty()) break;
            all.addAll(batch);
            if (batch.size() < PAGE_SIZE) break;
            page++;
        }
        return all.size() > maxRows ? all.subList(0, (int) maxRows) : all;
    }

    private void audit(String action, String entityType, UUID entityId, UUID actor,
                       Map<String, Object> metadata) {
        try {
            audit.record(AuditEvent.of(null, actor, action, entityType, entityId, null,
                    metadata != null ? metadata : Map.of(), null, null, null));
        } catch (Exception ex) {
            log.warn("No se pudo registrar audit {}: {}", action, ex.getMessage());
        }
    }

    private static String csv(Object value) {
        if (value == null) return "";
        String s = value.toString();
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    private static String toJsonInline(Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> en : new TreeMap<>(filters).entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append('"').append(en.getKey()).append("\": \"").append(en.getValue()).append('"');
        }
        return sb.append('}').toString();
    }

    private static String str(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Instant parseInstant(Object v) {
        String s = str(v);
        if (s == null) return null;
        try {
            return Instant.parse(s);
        } catch (Exception ignore) {
            // continúa
        }
        try {
            return LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (Exception ignore) {
            return null;
        }
    }

    private static int parseYear(Object v) {
        String s = str(v);
        if (s != null) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignore) {
                // continúa
            }
        }
        return LocalDate.now(ZoneOffset.UTC).getYear();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
