package com.micropay.backend.infrastructure.persistence.jpa.repositories;

import com.micropay.backend.infrastructure.persistence.jpa.entities.RefundJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RefundJpaRepository extends JpaRepository<RefundJpa, UUID> {

    List<RefundJpa> findBySourceTxIdOrderByCreatedAtAsc(UUID sourceTxId);
}
