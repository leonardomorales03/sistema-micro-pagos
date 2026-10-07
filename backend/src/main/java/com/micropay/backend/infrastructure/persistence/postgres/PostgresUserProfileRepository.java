package com.micropay.backend.infrastructure.persistence.postgres;

import com.micropay.backend.domain.entities.UserProfile;
import com.micropay.backend.domain.ports.out.UserProfileRepository;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.infrastructure.persistence.jpa.entities.UserProfileJpa;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.UserProfileJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PostgresUserProfileRepository implements UserProfileRepository {

    private final UserProfileJpaRepository jpa;

    public PostgresUserProfileRepository(UserProfileJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public UserProfile save(UserProfile p) {
        UserProfileJpa j = toJpa(p);
        return toDomain(jpa.save(j));
    }

    @Override
    public Optional<UserProfile> findByUserId(UserId u) {
        return jpa.findById(u.uuid()).map(this::toDomain);
    }

    private UserProfileJpa toJpa(UserProfile p) {
        UserProfileJpa j = new UserProfileJpa();
        j.setUserId(p.userId().uuid());
        j.setLanguage(p.language());
        j.setTheme(UserProfileJpa.Theme.valueOf(p.theme().name()));
        j.setPreferredCurrency(p.preferredCurrency());
        j.setTimezone(p.timezone());
        j.setPushEnabled(p.pushEnabled());
        j.setEmailNotifEnabled(p.emailNotifEnabled());
        j.setMarketingOptIn(p.marketingOptIn());
        j.setAddressLine(p.addressLine());
        j.setAddressCity(p.addressCity());
        j.setAddressCountry(p.addressCountry());
        j.setBillingTaxId(p.billingTaxId());
        j.setProfilePictureUrl(p.profilePictureUrl());
        j.setBio(p.bio());
        j.setReferralBonusClaimed(p.referralBonusClaimed());
        j.setCreatedAt(p.createdAt());
        j.setUpdatedAt(p.updatedAt());
        return j;
    }

    private UserProfile toDomain(UserProfileJpa j) {
        UserProfile d = new UserProfile(UserId.from(j.getUserId()));
        d.updatePreferences(j.getLanguage(), UserProfile.Theme.valueOf(j.getTheme().name()),
                j.getPreferredCurrency(), j.getTimezone(), j.isPushEnabled(),
                j.isEmailNotifEnabled(), j.isMarketingOptIn());
        d.updateContactAddress(j.getAddressLine(), j.getAddressCity(), j.getAddressCountry(),
                j.getBillingTaxId(), j.getProfilePictureUrl(), j.getBio());
        if (j.isReferralBonusClaimed()) d.claimReferralBonusIfEligible();
        return d;
    }
}
