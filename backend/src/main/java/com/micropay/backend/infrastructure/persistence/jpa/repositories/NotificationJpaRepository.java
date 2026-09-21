package com.micropay.backend.infrastructure.persistence.jpa.repositories;

import com.micropay.backend.infrastructure.persistence.jpa.entities.NotificationJpa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationJpaRepository extends JpaRepository<NotificationJpa, UUID> {

    Page<NotificationJpa> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<NotificationJpa> findByStatusInOrderByCreatedAtAsc(List<String> statuses);
}
