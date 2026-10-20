package com.micropay.backend.infrastructure.referrals;

import com.micropay.backend.domain.entities.Referral;
import com.micropay.backend.domain.ports.out.ReferralRepository;
import com.micropay.backend.infrastructure.persistence.jpa.entities.ReferralJpa;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.ReferralJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador Postgres del puerto {@link ReferralRepository} (tabla {@code referrals}, V13).
 */
@Repository
public class PostgresReferralRepository implements ReferralRepository {

    private final ReferralJpaRepository jpa;

    public PostgresReferralRepository(ReferralJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Referral save(Referral r) {
        return toDomain(jpa.save(toJpa(r)));
    }

    @Override
    public Optional<Referral> findByInviteeId(UUID inviteeId) {
        return jpa.findByInviteeId(inviteeId).map(this::toDomain);
    }

    @Override
    public Optional<Referral> lockByInviteeId(UUID inviteeId) {
        return jpa.findByInviteeIdForUpdate(inviteeId).map(this::toDomain);
    }

    @Override
    public Optional<Referral> findByCode(String code) {
        return jpa.findFirstByCodeOrderByCreatedAtAsc(code).map(this::toDomain);
    }

    @Override
    public List<Referral> listByReferrer(UUID referrerId, int page, int pageSize) {
        Pageable p = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, pageSize)));
        Page<ReferralJpa> rs = jpa.findByReferrerIdOrderByCreatedAtDesc(referrerId, p);
        return rs.getContent().stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByReferrerAndInvitee(UUID referrerId, UUID inviteeId) {
        return jpa.existsByReferrerIdAndInviteeId(referrerId, inviteeId);
    }

    // ──────────────────────── Mappers Domain ↔ JPA ────────────────────────

    private ReferralJpa toJpa(Referral d) {
        ReferralJpa j = new ReferralJpa();
        j.setId(d.id());
        j.setCode(d.code());
        j.setReferrerId(d.referrerId());
        j.setInviteeId(d.inviteeId());
        j.setStatus(ReferralJpa.ReferralStatus.valueOf(d.status().name()));
        j.setReferrerBonus(d.referrerBonus());
        j.setInviteeBonus(d.inviteeBonus());
        j.setBonusTxId(d.bonusTxId());
        j.setCreatedAt(d.createdAt() != null ? d.createdAt() : java.time.Instant.now());
        j.setQualifiedAt(d.qualifiedAt());
        return j;
    }

    private Referral toDomain(ReferralJpa j) {
        if (j == null) return null;
        return new Referral(
                j.getId(),
                j.getCode(),
                j.getReferrerId(),
                j.getInviteeId(),
                j.getStatus() != null ? Referral.ReferralStatus.valueOf(j.getStatus().name())
                        : Referral.ReferralStatus.PENDING,
                j.getReferrerBonus(),
                j.getInviteeBonus(),
                j.getBonusTxId(),
                j.getCreatedAt(),
                j.getQualifiedAt()
        );
    }
}
