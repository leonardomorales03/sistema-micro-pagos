package com.micropay.backend.infrastructure.persistence.jpa.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
public class UserProfileJpa {

    @Id
    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    @Column(name = "language", length = 5, nullable = false)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme", length = 16, nullable = false)
    private Theme theme;

    @Column(name = "preferred_currency", length = 3, nullable = false)
    private String preferredCurrency;

    @Column(name = "timezone", length = 64, nullable = false)
    private String timezone;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled;

    @Column(name = "email_notif_enabled", nullable = false)
    private boolean emailNotifEnabled;

    @Column(name = "marketing_opt_in", nullable = false)
    private boolean marketingOptIn;

    @Column(name = "address_line", length = 200)
    private String addressLine;

    @Column(name = "address_city", length = 100)
    private String addressCity;

    @Column(name = "address_country", length = 2)
    private String addressCountry;

    @Column(name = "billing_tax_id", length = 32)
    private String billingTaxId;

    @Column(name = "profile_picture_url", length = 512)
    private String profilePictureUrl;

    @Column(name = "bio", length = 200)
    private String bio;

    @Column(name = "referral_bonus_claimed", nullable = false)
    private boolean referralBonusClaimed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum Theme { LIGHT, DARK, SYSTEM }

    public UserProfileJpa() {}

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public Theme getTheme() { return theme; }
    public void setTheme(Theme theme) { this.theme = theme; }

    public String getPreferredCurrency() { return preferredCurrency; }
    public void setPreferredCurrency(String preferredCurrency) { this.preferredCurrency = preferredCurrency; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public boolean isPushEnabled() { return pushEnabled; }
    public void setPushEnabled(boolean pushEnabled) { this.pushEnabled = pushEnabled; }

    public boolean isEmailNotifEnabled() { return emailNotifEnabled; }
    public void setEmailNotifEnabled(boolean emailNotifEnabled) { this.emailNotifEnabled = emailNotifEnabled; }

    public boolean isMarketingOptIn() { return marketingOptIn; }
    public void setMarketingOptIn(boolean marketingOptIn) { this.marketingOptIn = marketingOptIn; }

    public String getAddressLine() { return addressLine; }
    public void setAddressLine(String addressLine) { this.addressLine = addressLine; }

    public String getAddressCity() { return addressCity; }
    public void setAddressCity(String addressCity) { this.addressCity = addressCity; }

    public String getAddressCountry() { return addressCountry; }
    public void setAddressCountry(String addressCountry) { this.addressCountry = addressCountry; }

    public String getBillingTaxId() { return billingTaxId; }
    public void setBillingTaxId(String billingTaxId) { this.billingTaxId = billingTaxId; }

    public String getProfilePictureUrl() { return profilePictureUrl; }
    public void setProfilePictureUrl(String profilePictureUrl) { this.profilePictureUrl = profilePictureUrl; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public boolean isReferralBonusClaimed() { return referralBonusClaimed; }
    public void setReferralBonusClaimed(boolean referralBonusClaimed) { this.referralBonusClaimed = referralBonusClaimed; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
