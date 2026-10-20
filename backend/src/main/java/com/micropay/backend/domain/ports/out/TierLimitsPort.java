package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.KycLevel;
import com.micropay.backend.domain.valueobjects.Money;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Tier limits port — devuelve límites operativos por KYC+País+Moneda.
 * Implementado por Postgres infra (tabla tier_limits V2).
 */
public interface TierLimitsPort {

    record TierLimit(
            KycLevel kycLevel,
            String countryISO2,
            Currency currency,
            BigDecimal maxSingleTx,
            BigDecimal maxDailyVolume,
            BigDecimal maxMonthlyVolume,
            BigDecimal maxWalletBalance,
            int maxTopupsPerDay,
            int maxWithdrawalsPerDay,
            BigDecimal withdrawalTierFeeBps
    ) {}

    Optional<TierLimit> findFor(KycLevel kycLevel, String countryISO2, Currency currency);

    /** Valida si monto de transacción única excede límite single-tx. */
    default void assertSingleTxAllowed(KycLevel kycLevel, String countryISO2, Money amount) {
        TierLimit t = findFor(kycLevel, countryISO2, amount.currency())
                .orElse(null);
        if (t != null && amount.amount().compareTo(t.maxSingleTx()) > 0) {
            throw new com.micropay.backend.domain.exceptions.BusinessRuleViolationException(
                    "TIER_LIMIT_SINGLE_TX_EXCEEDED",
                    "Monto transacción %s %s excede límite KYC %s país %s = %s"
                            .formatted(amount.amount(), amount.currency(), kycLevel, countryISO2, t.maxSingleTx()));
        }
    }
}
