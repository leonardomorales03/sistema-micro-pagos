package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.entities.AuditEvent;

import java.util.List;
import java.util.UUID;

/**
 * Port OUT — Repositorio de auditoría (append-only).
 * Implementado por Infrastructure Adapter → PostgresAuditEventRepository.
 */
public interface AuditEventRepository {

    /** Persiste un evento. Sólo INSERT — nunca update/delete. */
    void record(AuditEvent e);

    /** Lista eventos filtrados (cualquier filtro null = sin restricción), paginado DESC por createdAt. */
    List<AuditEvent> list(String entityType, UUID entityId, UUID actorUserId, int page, int pageSize);
}
