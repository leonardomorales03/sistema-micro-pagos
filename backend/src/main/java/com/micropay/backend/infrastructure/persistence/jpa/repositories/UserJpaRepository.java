package com.micropay.backend.infrastructure.persistence.jpa.repositories;

import com.micropay.backend.infrastructure.persistence.jpa.entities.UserJpa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserJpa, UUID> {

    Optional<UserJpa> findByEmailIgnoreCase(String email);

    Optional<UserJpa> findByReferralCode(String referralCode);

    Page<UserJpa> findAllBy(Pageable pageable);

    boolean existsByEmailIgnoreCase(String email);
}
