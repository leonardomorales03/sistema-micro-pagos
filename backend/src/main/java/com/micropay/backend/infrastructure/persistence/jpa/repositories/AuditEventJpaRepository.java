package com.micropay.backend.infrastructure.persistence.jpa.repositories;

import com.micropay.backend.infrastructure.persistence.jpa.entities.AuditEventJpa;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AuditEventJpaRepository extends JpaRepository<AuditEventJpa, UUID> {

    @Query("SELECT a FROM AuditEventJpa a "
            + "WHERE (:entityType IS NULL OR a.entityType = :entityType) "
            + "AND (:entityId IS NULL OR a.entityId = :entityId) "
            + "AND (:actorUserId IS NULL OR a.actorUserId = :actorUserId) "
            + "ORDER BY a.createdAt DESC")
    List<AuditEventJpa> search(@Param("entityType") String entityType,
                               @Param("entityId") UUID entityId,
                               @Param("actorUserId") UUID actorUserId,
                               Pageable pageable);
}
