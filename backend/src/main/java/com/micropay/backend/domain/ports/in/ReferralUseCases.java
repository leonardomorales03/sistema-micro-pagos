package com.micropay.backend.domain.ports.in;

import com.micropay.backend.domain.entities.Referral;
import com.micropay.backend.domain.valueobjects.Money;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Casos de uso IN de Referrals & Rewards (T27 / Requisito 31).
 * Implementado en la capa de aplicación → {@code ReferralService}.
 */
public interface ReferralUseCases {

    /**
     * Registra la atribución de un invitee a un referrer al completar el signup.
     * IDEMPOTENTE: si el par (referrer, invitee) o el invitee ya existen, no falla
     * y devuelve el referral existente.
     */
    Referral linkOnSignup(UUID referrerId, UUID inviteeId);

    /**
     * Paga (una sola vez) el bono al referrer cuando el invitee cumple la acción
     * cualificante ({@code qualifyingAmount} ≥ umbral en su moneda).
     * IDEMPOTENTE: si el referral ya está QUALIFIED devuelve {@link Optional#empty()}.
     * Devuelve el referral QUALIFIED si se pagó el bono.
     */
    Optional<Referral> payQualifyingBonus(UUID inviteeUserId, Money qualifyingAmount);

    /** Referidos de un usuario (como referrer), más recientes primero. */
    List<Referral> listMine(UUID userId);

    /** Resumen para el dashboard (código propio, enlace de invitación, conteos y bonos). */
    Optional<ReferralSummary> referralSummary(UUID userId);

    /**
     * Resumen del programa de referidos de un usuario.
     *
     * @param referralCode      código propio de referral
     * @param referralLink      enlace de invitación construido a partir del código
     * @param totalReferrals    nº total de referidos atribuidos
     * @param qualifiedCount    nº de referidos que ya generaron bono
     * @param pendingCount      nº de referidos pendientes de cualificar
     * @param totalBonusEarned  suma de bonos de referrer pagados (moneda no almacenada en referrals)
     */
    record ReferralSummary(
            String referralCode,
            String referralLink,
            long totalReferrals,
            long qualifiedCount,
            long pendingCount,
            BigDecimal totalBonusEarned
    ) {}
}
