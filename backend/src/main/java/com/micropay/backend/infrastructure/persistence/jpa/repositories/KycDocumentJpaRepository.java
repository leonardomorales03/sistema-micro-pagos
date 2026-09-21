package com.micropay.backend.infrastructure.persistence.jpa.repositories;

import com.micropay.backend.infrastructure.persistence.jpa.entities.KycDocumentJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KycDocumentJpaRepository extends JpaRepository<KycDocumentJpa, UUID> {

    List<KycDocumentJpa> findByUserIdOrderBySubmittedAtDesc(UUID userId);

    boolean existsByTypeAndNumberAndCountry(String type, String number, String country);
}
