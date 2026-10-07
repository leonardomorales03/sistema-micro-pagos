package com.micropay.backend.domain.entities;

import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.valueobjects.UserId;

import java.time.Instant;

/**
 * Perfil extendido de usuario (1:1 con User). Se almacena en user_profiles.
 * Preferencias, settings, KYC estado avanzado y datos opcionales.
 */
public final class UserProfile {

    public enum Theme { LIGHT, DARK, SYSTEM }

    private final UserId userId;
    private String language;            // es_CO / en_US / pt_BR
    private Theme theme;
    private String preferredCurrency;   // USD, COP
    private String timezone;
    private boolean pushEnabled;
    private boolean emailNotifEnabled;
    private boolean marketingOptIn;
    private String addressLine;
    private String addressCity;
    private String addressCountry; // ISO2
    private String billingTaxId;
    private String profilePictureUrl;
    private String bio;
    private boolean referralBonusClaimed;
    private final Instant createdAt;
    private Instant updatedAt;

    public UserProfile(UserId userId) {
        if (userId == null) throw new IllegalArgumentException("userId");
        this.userId = userId;
        this.language = "es_CO";
        this.theme = Theme.SYSTEM;
        this.preferredCurrency = "USD";
        this.timezone = "America/Bogota";
        this.pushEnabled = true;
        this.emailNotifEnabled = true;
        this.marketingOptIn = false;
        this.referralBonusClaimed = false;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UserId userId() { return userId; }
    public String language() { return language; }
    public Theme theme() { return theme; }
    public String preferredCurrency() { return preferredCurrency; }
    public String timezone() { return timezone; }
    public boolean pushEnabled() { return pushEnabled; }
    public boolean emailNotifEnabled() { return emailNotifEnabled; }
    public boolean marketingOptIn() { return marketingOptIn; }
    public String addressLine() { return addressLine; }
    public String addressCity() { return addressCity; }
    public String addressCountry() { return addressCountry; }
    public String billingTaxId() { return billingTaxId; }
    public String profilePictureUrl() { return profilePictureUrl; }
    public String bio() { return bio; }
    public boolean referralBonusClaimed() { return referralBonusClaimed; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public void updatePreferences(String language, Theme theme, String preferredCurrency,
                                  String timezone, boolean pushEnabled, boolean emailNotifEnabled,
                                  boolean marketingOptIn) {
        if (language == null || !language.matches("^(es_CO|es_ES|en_US|pt_BR)$"))
            throw new BusinessRuleViolationException("PROFILE_LANGUAGE_INVALID", "language inválido");
        if (theme == null) throw new IllegalArgumentException("theme");
        if (preferredCurrency == null || preferredCurrency.isBlank())
            throw new IllegalArgumentException("preferredCurrency");
        if (timezone == null || timezone.isBlank())
            throw new IllegalArgumentException("timezone");
        this.language = language;
        this.theme = theme;
        this.preferredCurrency = preferredCurrency;
        this.timezone = timezone;
        this.pushEnabled = pushEnabled;
        this.emailNotifEnabled = emailNotifEnabled;
        this.marketingOptIn = marketingOptIn;
        touch();
    }

    public void updateContactAddress(String addressLine, String addressCity, String addressCountry,
                                     String billingTaxId, String profilePictureUrl, String bio) {
        if (addressCountry != null && addressCountry.length() != 2)
            throw new BusinessRuleViolationException("PROFILE_COUNTRY_INVALID",
                    "addressCountry debe ser ISO2 2 chars");
        this.addressLine = addressLine;
        this.addressCity = addressCity;
        this.addressCountry = addressCountry;
        this.billingTaxId = billingTaxId;
        this.profilePictureUrl = profilePictureUrl;
        this.bio = bio != null && bio.length() > 200 ? bio.substring(0, 200) : bio;
        touch();
    }

    public void claimReferralBonusIfEligible() {
        if (this.referralBonusClaimed) return;
        this.referralBonusClaimed = true;
        touch();
    }

    private void touch() { this.updatedAt = Instant.now(); }
}
