package com.micropay.backend.infrastructure.persistence.jpa.repositories;

import com.micropay.backend.infrastructure.persistence.jpa.entities.AccountMoveJpa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AccountMoveJpaRepository extends JpaRepository<AccountMoveJpa, UUID> {

    @Query("SELECT m FROM AccountMoveJpa m WHERE m.transactionId = :txId ORDER BY m.createdAt ASC")
    List<AccountMoveJpa> findByTransactionIdOrderByCreatedAtAsc(@Param("txId") UUID transactionId);
}
