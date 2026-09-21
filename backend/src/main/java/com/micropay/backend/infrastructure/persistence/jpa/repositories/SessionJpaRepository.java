package com.micropay.backend.infrastructure.persistence.jpa.repositories;

import com.micropay.backend.infrastructure.persistence.jpa.entities.SessionJpa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionJpaRepository extends JpaRepository<SessionJpa, UUID> {

    Optional<SessionJpa> findByRefreshTokenHash(String refreshTokenHash);

    List<SessionJpa> findByUserIdOrderByIssuedAtDesc(UUID userId);

    @Modifying
    @Query("UPDATE SessionJpa s SET s.revokedAt = :now, s.revokedReason = :reason " +
            "WHERE s.userId = :userId AND s.revokedAt IS NULL AND s.expiresAt > :now")
    int revokeAllActiveForUser(@Param("userId") UUID userId,
                               @Param("reason") String reason,
                               @Param("now") Instant now);
}
