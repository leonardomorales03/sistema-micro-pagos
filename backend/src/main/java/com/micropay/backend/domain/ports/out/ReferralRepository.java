package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.entities.Referral;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port OUT — Repositorio de referidos (tabla {@code referrals}, V13).
 * Implementado por Infrastructure Adapter → {@code PostgresReferralRepository}.
 */
public interface ReferralRepository {

    Referral save(Referral r);

    /** Referral atribuido a un invitee (un invitee → a lo sumo un referral). */
    Optional<Referral> findByInviteeId(UUID inviteeId);

    /**
     * Carga el referral del invitee con lock pesimista (SELECT ... FOR UPDATE).
     * Usado por el pago del bono para serializar intentos concurrentes e idempotencia.
     */
    Optional<Referral> lockByInviteeId(UUID inviteeId);

    /** Primer referral creado con un código de referrer (dashboard/atribución). */
    Optional<Referral> findByCode(String code);

    /** Listado de referidos de un referrer, más recientes primero, paginado. */
    List<Referral> listByReferrer(UUID referrerId, int page, int pageSize);

    boolean existsByReferrerAndInvitee(UUID referrerId, UUID inviteeId);
}
