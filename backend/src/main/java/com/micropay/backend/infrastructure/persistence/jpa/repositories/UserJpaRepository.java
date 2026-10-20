package com.micropay.backend.infrastructure.persistence.jpa.repositories;

import com.micropay.backend.infrastructure.persistence.jpa.entities.UserJpa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserJpa, UUID> {

    Optional<UserJpa> findByEmailIgnoreCase(String email);

    Optional<UserJpa> findByReferralCode(String referralCode);

    Page<UserJpa> findAllBy(Pageable pageable);

    boolean existsByEmailIgnoreCase(String email);

    /** Búsqueda admin parcial por email / full_name / phone / id (T24). */
    @Query(value = "SELECT * FROM users u "
            + "WHERE u.email::text ILIKE CONCAT('%', :q, '%') "
            + "   OR u.full_name ILIKE CONCAT('%', :q, '%') "
            + "   OR COALESCE(u.phone_number, '') ILIKE CONCAT('%', :q, '%') "
            + "   OR u.id::text ILIKE CONCAT('%', :q, '%') "
            + "ORDER BY u.created_at DESC",
            countQuery = "SELECT count(*) FROM users u "
            + "WHERE u.email::text ILIKE CONCAT('%', :q, '%') "
            + "   OR u.full_name ILIKE CONCAT('%', :q, '%') "
            + "   OR COALESCE(u.phone_number, '') ILIKE CONCAT('%', :q, '%') "
            + "   OR u.id::text ILIKE CONCAT('%', :q, '%')",
            nativeQuery = true)
    Page<UserJpa> search(@Param("q") String q, Pageable pageable);
}
