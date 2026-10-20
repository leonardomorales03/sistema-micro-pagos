package com.micropay.backend.domain.entities;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Referral — vínculo de atribución entre un referrer y un invitado (invitee).
 * <p>
 * Java puro (sin Spring/JPA). Se persiste en la tabla {@code referrals} (V13).
 * <p>
 * Reglas de negocio (Requisito 31) reconciliadas con {@code micropay.referrals.*}:
 * <ul>
 *   <li>Un invitee sólo puede estar atribuido a un referrer (un solo par).</li>
 *   <li>El bono se paga UNA SOLA VEZ por par: status PENDING → QUALIFIED.</li>
 *   <li>{@code referrerBonus} se calcula como {@code reward-percent × monto cualificante}
 *       (la "qualifying action" configurada es un retiro ≥ umbral).</li>
 * </ul>
 * Inmutable (record); las transiciones devuelven una nueva instancia.
 */
public record Referral(
        UUID id,
        String code,
        UUID referrerId,
        UUID inviteeId,
        ReferralStatus status,
        BigDecimal referrerBonus,
        BigDecimal inviteeBonus,
        UUID bonusTxId,
        Instant createdAt,
        Instant qualifiedAt
) {

    /** Estado del referral (coincide con el CHECK de la tabla V13). */
    public enum ReferralStatus { PENDING, QUALIFIED, EXPIRED }

    /** Crea un referral recién atribuido (PENDING). */
    public static Referral pending(String code, UUID referrerId, UUID inviteeId) {
        return new Referral(
                UUID.randomUUID(), code, referrerId, inviteeId,
                ReferralStatus.PENDING, null, null, null, Instant.now(), null);
    }

    /** Devuelve una copia marcada como QUALIFIED con el bono pagado. */
    public Referral qualified(BigDecimal referrerBonus, UUID bonusTxId, Instant when) {
        return new Referral(
                id, code, referrerId, inviteeId,
                ReferralStatus.QUALIFIED, referrerBonus, inviteeBonus, bonusTxId,
                createdAt, when != null ? when : Instant.now());
    }
}
