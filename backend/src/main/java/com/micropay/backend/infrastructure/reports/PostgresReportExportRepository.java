package com.micropay.backend.infrastructure.reports;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micropay.backend.domain.entities.ReportExport;
import com.micropay.backend.domain.ports.out.ReportExportRepository;
import com.micropay.backend.infrastructure.persistence.jpa.entities.ReportExportJpa;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.ReportExportJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador Postgres del puerto {@link ReportExportRepository} (tabla {@code report_exports}, V11).
 * Sigue el patrón de {@code PostgresRiskIncidentRepository} para la serialización JSONB.
 */
@Repository
public class PostgresReportExportRepository implements ReportExportRepository {

    private static final Logger log = LoggerFactory.getLogger(PostgresReportExportRepository.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ReportExportJpaRepository jpa;

    public PostgresReportExportRepository(ReportExportJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public ReportExport save(ReportExport export) {
        return toDomain(jpa.save(toJpa(export)));
    }

    @Override
    public Optional<ReportExport> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<ReportExport> findByToken(String downloadToken) {
        if (downloadToken == null || downloadToken.isBlank()) return Optional.empty();
        return jpa.findByDownloadToken(downloadToken).map(this::toDomain);
    }

    @Override
    public List<ReportExport> list(UUID requestedBy, int page, int pageSize) {
        int p = Math.max(0, page);
        int size = Math.min(200, Math.max(1, pageSize));
        PageRequest pr = PageRequest.of(p, size);
        return (requestedBy == null
                ? jpa.findAllByOrderByCreatedAtDesc(pr)
                : jpa.findByRequestedByOrderByCreatedAtDesc(requestedBy, pr))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<ReportExport> findQueued(int limit) {
        int size = Math.min(200, Math.max(1, limit));
        return jpa.findByStatusOrderByCreatedAtAsc(ReportExport.QUEUED, PageRequest.of(0, size))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    // ──────────────────────── Mappers Domain ↔ JPA ────────────────────────

    private ReportExportJpa toJpa(ReportExport d) {
        ReportExportJpa j = new ReportExportJpa();
        j.setId(d.id());
        j.setRequestedBy(d.requestedBy());
        j.setType(d.type());
        j.setFilters(toJson(d.filters()));
        j.setStatus(d.status());
        j.setFilePath(d.filePath());
        j.setDownloadToken(d.downloadToken());
        j.setRowCount(d.rowCount());
        j.setErrorMessage(d.errorMessage());
        j.setCreatedAt(d.createdAt() != null ? d.createdAt() : Instant.now());
        j.setStartedAt(d.startedAt());
        j.setCompletedAt(d.completedAt());
        j.setExpiresAt(d.expiresAt());
        return j;
    }

    private ReportExport toDomain(ReportExportJpa j) {
        if (j == null) return null;
        return new ReportExport(
                j.getId(),
                j.getRequestedBy(),
                j.getType(),
                fromJson(j.getFilters()),
                j.getStatus(),
                j.getFilePath(),
                j.getDownloadToken(),
                j.getRowCount(),
                j.getErrorMessage(),
                j.getCreatedAt(),
                j.getStartedAt(),
                j.getCompletedAt(),
                j.getExpiresAt()
        );
    }

    private String toJson(Map<String, Object> filters) {
        try {
            return MAPPER.writeValueAsString(filters == null ? Map.of() : filters);
        } catch (Exception e) {
            log.warn("No se pudo serializar filtros de export: {}", e.getMessage());
            return "{}";
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("No se pudo deserializar filtros de export: {}", e.getMessage());
            return Map.of();
        }
    }
}
