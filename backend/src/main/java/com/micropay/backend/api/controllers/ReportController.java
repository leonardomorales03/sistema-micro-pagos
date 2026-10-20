package com.micropay.backend.api.controllers;

import com.micropay.backend.api.dtos.ReportDtos;
import com.micropay.backend.domain.entities.ReconciliationMismatch;
import com.micropay.backend.domain.entities.ReconciliationRun;
import com.micropay.backend.domain.entities.ReportExport;
import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.ports.in.ReportUseCases;
import com.micropay.backend.domain.ports.out.ReportStoragePort;
import com.micropay.backend.domain.valueobjects.CashflowReport;
import com.micropay.backend.domain.valueobjects.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoints de Reports, Exports CSV + Reconciliación (T25 / Requisitos 28.x).
 * <p>
 * Bajo {@code /api/v1/admin/reports} y {@code /api/v1/admin/reconciliation}; todos
 * requieren autenticación JWT y rol FINANCE / SUPER_ADMIN / ADMIN.
 */
@Tag(name = "Admin Reports", description = "Exports CSV asíncronos, cashflow diario y reconciliación")
@RestController
@RequestMapping("/api/v1/admin")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

    private static final String FINANCE_ROLES = "hasAnyRole('FINANCE','SUPER_ADMIN','ADMIN')";

    private final ReportUseCases reports;
    private final ReportStoragePort storage;

    public ReportController(ReportUseCases reports, ReportStoragePort storage) {
        this.reports = reports;
        this.storage = storage;
    }

    // ═════════════════════════ Exports CSV ═════════════════════════

    @PostMapping("/reports/exports")
    @PreAuthorize(FINANCE_ROLES)
    @Operation(summary = "Encolar una exportación CSV asíncrona",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Export encolado (QUEUED)"),
            @ApiResponse(responseCode = "403", description = "Rol insuficiente"),
            @ApiResponse(responseCode = "422", description = "Tipo de exportación inválido")
    })
    public ResponseEntity<ReportDtos.ReportExportResponse> requestExport(
            @RequestBody ReportDtos.RequestExportRequest req,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        ReportExport created = reports.requestExport(
                req != null ? req.type() : null,
                req != null ? req.filters() : null,
                currentUser != null ? currentUser.uuid() : null);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(toExportView(created));
    }

    @GetMapping("/reports/exports")
    @PreAuthorize(FINANCE_ROLES)
    @Operation(summary = "Listar exportaciones (propias; todas si SUPER_ADMIN/ADMIN)",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<List<ReportDtos.ReportExportResponse>> listExports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(hidden = true) @CurrentUser UserId currentUser,
            @Parameter(hidden = true) Authentication auth) {
        UUID scope = isElevated(auth) ? null : (currentUser != null ? currentUser.uuid() : null);
        return ResponseEntity.ok(reports.listExports(scope, page, pageSize).stream()
                .map(ReportController::toExportView).toList());
    }

    @GetMapping("/reports/exports/download/{token}")
    @PreAuthorize(FINANCE_ROLES)
    @Operation(summary = "Descargar el CSV de una exportación por token",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "CSV"),
            @ApiResponse(responseCode = "404", description = "Token inexistente"),
            @ApiResponse(responseCode = "409", description = "Export aún no está listo"),
            @ApiResponse(responseCode = "410", description = "Enlace expirado")
    })
    public ResponseEntity<byte[]> download(
            @PathVariable("token") String token) {
        ReportExport export;
        try {
            export = reports.getByToken(token);
        } catch (BusinessRuleViolationException ex) {
            if ("REPORT_EXPORT_EXPIRED".equals(ex.getCode())) {
                return ResponseEntity.status(HttpStatus.GONE).build();
            }
            if ("REPORT_EXPORT_NOT_READY".equals(ex.getCode())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }
            throw ex;
        }
        byte[] content = storage.read(export.filePath());
        String fileName = export.type().toLowerCase() + "_" + export.id() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(content);
    }

    // ═════════════════════════ Cashflow diario ═════════════════════════

    @GetMapping("/reports/cashflow")
    @PreAuthorize(FINANCE_ROLES)
    @Operation(summary = "Dashboard diario de cashflow (créditos/débitos/net/fees por tipo)",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ReportDtos.CashflowResponse> cashflow(
            @RequestParam(required = false) String date) {
        LocalDate day = (date == null || date.isBlank())
                ? LocalDate.now(ZoneOffset.UTC)
                : LocalDate.parse(date.trim());
        CashflowReport report = reports.cashflow(day);
        return ResponseEntity.ok(new ReportDtos.CashflowResponse(
                report.date(),
                report.creditedByType(),
                report.debitedByType(),
                report.net(),
                report.feesByType()));
    }

    // ═════════════════════════ Reconciliación ═════════════════════════

    @PostMapping("/reconciliation/runs")
    @PreAuthorize(FINANCE_ROLES)
    @Operation(summary = "Disparar una reconciliación interno vs proveedor",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Run ejecutado"),
            @ApiResponse(responseCode = "403", description = "Rol insuficiente"),
            @ApiResponse(responseCode = "422", description = "provider requerido")
    })
    public ResponseEntity<ReportDtos.ReconciliationRunResponse> startRun(
            @RequestBody ReportDtos.StartReconciliationRequest req,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        Instant[] period = defaultPeriod(req != null ? req.periodStart() : null,
                req != null ? req.periodEnd() : null);
        ReconciliationRun run = reports.runReconciliation(
                req != null ? req.provider() : null,
                period[0], period[1],
                currentUser != null ? currentUser.uuid() : null);
        return ResponseEntity.ok(toRunView(run));
    }

    @GetMapping("/reconciliation/runs")
    @PreAuthorize(FINANCE_ROLES)
    @Operation(summary = "Listar ejecuciones de reconciliación",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<List<ReportDtos.ReconciliationRunResponse>> listRuns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(reports.listRuns(page, pageSize).stream()
                .map(ReportController::toRunView).toList());
    }

    @GetMapping("/reconciliation/runs/{runId}/mismatches")
    @PreAuthorize(FINANCE_ROLES)
    @Operation(summary = "Listar discrepancias de una ejecución",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<List<ReportDtos.ReconciliationMismatchResponse>> listMismatches(
            @PathVariable("runId") UUID runId) {
        return ResponseEntity.ok(reports.listMismatches(runId).stream()
                .map(ReportController::toMismatchView).toList());
    }

    @PatchMapping("/reconciliation/mismatches/{mismatchId}")
    @PreAuthorize(FINANCE_ROLES)
    @Operation(summary = "Resolver / investigar una discrepancia (note obligatoria si RESOLVED)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Discrepancia actualizada"),
            @ApiResponse(responseCode = "404", description = "Discrepancia no encontrada"),
            @ApiResponse(responseCode = "422", description = "status inválido o note faltante")
    })
    public ResponseEntity<ReportDtos.ReconciliationMismatchResponse> resolveMismatch(
            @PathVariable("mismatchId") UUID mismatchId,
            @RequestBody ReportDtos.ResolveMismatchRequest req,
            @Parameter(hidden = true) @CurrentUser UserId currentUser) {
        ReconciliationMismatch saved = reports.resolveMismatch(
                mismatchId,
                req != null ? req.status() : null,
                req != null ? req.note() : null,
                currentUser != null ? currentUser.uuid() : null);
        return ResponseEntity.ok(toMismatchView(saved));
    }

    // ═════════════════════════ Helpers ═════════════════════════

    private static Instant[] defaultPeriod(Instant start, Instant end) {
        if (start != null && end != null) {
            return new Instant[]{start, end};
        }
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        Instant defStart = start != null ? start : yesterday.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant defEnd = end != null ? end : yesterday.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new Instant[]{defStart, defEnd};
    }

    private static boolean isElevated(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> {
            String r = a.getAuthority();
            return "ROLE_SUPER_ADMIN".equals(r) || "ROLE_ADMIN".equals(r);
        });
    }

    private static ReportDtos.ReportExportResponse toExportView(ReportExport e) {
        return new ReportDtos.ReportExportResponse(
                e.id(), e.requestedBy(), e.type(), e.filters(), e.status(),
                e.filePath(), e.downloadToken(), e.rowCount(), e.errorMessage(),
                e.createdAt(), e.startedAt(), e.completedAt(), e.expiresAt());
    }

    private static ReportDtos.ReconciliationRunResponse toRunView(ReconciliationRun r) {
        return new ReportDtos.ReconciliationRunResponse(
                r.id(), r.triggeredBy(), r.provider(), r.status(),
                r.periodStart(), r.periodEnd(),
                r.internalCount(), r.providerCount(), r.mismatchCount(),
                r.notes(), r.createdAt(), r.completedAt());
    }

    private static ReportDtos.ReconciliationMismatchResponse toMismatchView(ReconciliationMismatch m) {
        return new ReportDtos.ReconciliationMismatchResponse(
                m.id(), m.runId(), m.kind(), m.internalTxId(), m.providerRef(),
                m.internalAmount(), m.providerAmount(), m.currency(), m.status(),
                m.resolutionNote(), m.resolvedBy(), m.createdAt(), m.updatedAt());
    }
}
