package com.micropay.backend.infrastructure.persistence.postgres;

import com.micropay.backend.domain.ports.out.TierLimitsPort;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.KycLevel;
import com.micropay.backend.infrastructure.persistence.jpa.entities.TierLimitJpa;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.TierLimitJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PostgresTierLimitsAdapter implements TierLimitsPort {

    private final TierLimitJpaRepository jpa;

    public PostgresTierLimitsAdapter(TierLimitJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<TierLimit> findFor(KycLevel kycLevel, String countryISO2, Currency currency) {
        TierLimitJpa.KycLevelEnum en = TierLimitJpa.KycLevelEnum.valueOf(kycLevel.name());
        return jpa.findByKycLevelAndCountryAndCurrency(en, countryISO2, currency.isoCode())
                .map(this::toDomain);
    }

    private TierLimit toDomain(TierLimitJpa j) {
        return new TierLimit(
                KycLevel.valueOf(j.getKycLevel().name()),
                j.getCountry(),
                Currency.valueOf(j.getCurrency()),
                j.getMaxSingleTx(),
                j.getMaxDailyVolume(),
                j.getMaxMonthlyVolume(),
                j.getMaxWalletBalance(),
                j.getMaxTopupsPerDay(),
                j.getMaxWithdrawalsPer(),
                j.getWithdrawalTierFee()
        );
    }
}
