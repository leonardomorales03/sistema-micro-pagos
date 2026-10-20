package com.micropay.backend.infrastructure.persistence.jpa.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA de {@code report_exports} (V11, T25). Los {@code filters} se mapean a JSONB
 * (mismo patrón que {@link UserNotificationJpa}).
 */
@Entity
@Table(name = "report_exports")
public class ReportExportJpa {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "requested_by", columnDefinition = "uuid", nullable = false)
    private UUID requestedBy;

    @Column(name = "type", length = 40, nullable = false)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filters", columnDefinition = "jsonb")
    private String filters;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "file_path", length = 1024)
    private String filePath;

    @Column(name = "download_token", length = 128, unique = true)
    private String downloadToken;

    @Column(name = "row_count")
    private Long rowCount;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public ReportExportJpa() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getRequestedBy() { return requestedBy; }
    public void setRequestedBy(UUID requestedBy) { this.requestedBy = requestedBy; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getFilters() { return filters; }
    public void setFilters(String filters) { this.filters = filters; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getDownloadToken() { return downloadToken; }
    public void setDownloadToken(String downloadToken) { this.downloadToken = downloadToken; }

    public Long getRowCount() { return rowCount; }
    public void setRowCount(Long rowCount) { this.rowCount = rowCount; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
