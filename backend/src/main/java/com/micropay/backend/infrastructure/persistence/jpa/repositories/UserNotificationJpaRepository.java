package com.micropay.backend.infrastructure.persistence.jpa.repositories;

import com.micropay.backend.infrastructure.persistence.jpa.entities.UserNotificationJpa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface UserNotificationJpaRepository extends JpaRepository<UserNotificationJpa, UUID> {

    Page<UserNotificationJpa> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UserNotificationJpa n set n.status = :readStatus, n.readAt = :now " +
           "where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") UUID userId,
                    @Param("readStatus") UserNotificationJpa.NotificationStatusJpa readStatus,
                    @Param("now") Instant now);
}
