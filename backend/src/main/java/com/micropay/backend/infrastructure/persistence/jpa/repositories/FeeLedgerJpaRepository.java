package com.micropay.backend.infrastructure.persistence.jpa.repositories;

import com.micropay.backend.infrastructure.persistence.jpa.entities.FeeLedgerJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeeLedgerJpaRepository extends JpaRepository<FeeLedgerJpa, UUID> {

    List<FeeLedgerJpa> findByTransactionIdOrderByCreatedAtAsc(UUID transactionId);
}
