package com.micropay.backend.infrastructure.persistence.jpa.repositories;

import com.micropay.backend.infrastructure.persistence.jpa.entities.ReferralJpa;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReferralJpaRepository extends JpaRepository<ReferralJpa, UUID> {

    Optional<ReferralJpa> findByInviteeId(UUID inviteeId);

    /** SELECT ... FOR UPDATE — serializa el pago del bono por invitee (idempotencia). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReferralJpa r where r.inviteeId = :inviteeId")
    Optional<ReferralJpa> findByInviteeIdForUpdate(@Param("inviteeId") UUID inviteeId);

    Optional<ReferralJpa> findFirstByCodeOrderByCreatedAtAsc(String code);

    Page<ReferralJpa> findByReferrerIdOrderByCreatedAtDesc(UUID referrerId, Pageable pageable);

    List<ReferralJpa> findByReferrerId(UUID referrerId);

    boolean existsByReferrerIdAndInviteeId(UUID referrerId, UUID inviteeId);
}
