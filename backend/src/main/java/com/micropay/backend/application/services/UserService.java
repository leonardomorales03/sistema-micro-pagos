package com.micropay.backend.application.services;

import com.micropay.backend.domain.entities.User;
import com.micropay.backend.domain.entities.UserProfile;
import com.micropay.backend.domain.exceptions.UserNotFoundException;
import com.micropay.backend.domain.ports.in.UserUseCases;
import com.micropay.backend.domain.ports.out.KycProviderPort;
import com.micropay.backend.domain.ports.out.NotificationAdapter;
import com.micropay.backend.domain.ports.out.TierLimitsPort;
import com.micropay.backend.domain.ports.out.UserProfileRepository;
import com.micropay.backend.domain.ports.out.UserRepository;
import com.micropay.backend.domain.valueobjects.DocumentNumber;
import com.micropay.backend.domain.valueobjects.Email;
import com.micropay.backend.domain.valueobjects.KycLevel;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.infrastructure.security.VerifiableTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class UserService implements UserUseCases {

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final NotificationAdapter notifications;
    private final PasswordEncoder passwordEncoder;
    private final VerifiableTokenService tokens;
    private final KycProviderPort kyc;
    private final TierLimitsPort tierLimits;

    public UserService(UserRepository users,
                       UserProfileRepository profiles,
                       NotificationAdapter notifications,
                       PasswordEncoder passwordEncoder,
                       VerifiableTokenService tokens,
                       KycProviderPort kyc,
                       TierLimitsPort tierLimits) {
        this.users = users;
        this.profiles = profiles;
        this.notifications = notifications;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.kyc = kyc;
        this.tierLimits = tierLimits;
    }

    @Override
    @Transactional
    public User register(Email email, String rawPassword, String fullName, UserId referredBy) {
        if (users.existsByEmail(email)) {
            throw new com.micropay.backend.domain.exceptions.BusinessRuleViolationException(
                    "EMAIL_ALREADY_REGISTERED",
                    "El correo %s ya está registrado".formatted(email.value()));
        }
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new com.micropay.backend.domain.exceptions.BusinessRuleViolationException(
                    "PASSWORD_TOO_SHORT",
                    "Password debe tener mínimo 8 caracteres");
        }
        if (referredBy != null && !users.findById(referredBy).isPresent()) {
            referredBy = null;
        }
        String bcryptHash = passwordEncoder.encode(rawPassword);
        User created = User.registerNew(email, bcryptHash, fullName, referredBy);
        User saved = users.save(created);
        UserProfile p = new UserProfile(saved.id());
        profiles.save(p);

        String verifyToken = tokens.issueToken(VerifiableTokenService.Purpose.VERIFY_EMAIL,
                saved.id(), 10L * 60L * 1000L);

        notifications.send(new NotificationAdapter.NotificationRequest(
                saved.id(),
                NotificationAdapter.Channel.EMAIL,
                "WELCOME_VERIFY_EMAIL",
                Map.of(
                        "email", saved.email().value(),
                        "full_name", saved.fullName(),
                        "referral_code", saved.referralCode(),
                        "verify_token", verifyToken
                )
        ));
        return saved;
    }

    @Override public Optional<User> findById(UserId id) { return users.findById(id); }
    @Override public Optional<User> findByEmail(Email email) { return users.findByEmail(email); }
    @Override public Optional<User> findByReferralCode(String referralCode) {
        return users.findByReferralCode(referralCode);
    }
    @Override public List<User> listUsers(int page, int pageSize) {
        return users.list(Math.max(0, page), Math.min(100, Math.max(1, pageSize)));
    }

    @Override
    @Transactional
    public void verifyEmail(UserId userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId.uuid()));
        user.verifyEmail();
        users.save(user);
    }

    /**
     * Nuevo endpoint: verify email vía token firmado (no UUID público).
     * Retorna el UserId verificado o empty si token inválido.
     */
    @Transactional
    public Optional<UserId> verifyEmailByToken(String token) {
        Optional<UserId> uid = tokens.verifyToken(VerifiableTokenService.Purpose.VERIFY_EMAIL, token);
        uid.ifPresent(this::verifyEmail);
        return uid;
    }

    @Override
    @Transactional
    public void blockUser(UserId userId, String reason, UserId byAdminId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId.uuid()));
        user.block(reason, byAdminId == null ? null : byAdminId.uuid());
        users.save(user);
    }

    @Override
    @Transactional
    public void unblockUser(UserId userId, UserId byAdminId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId.uuid()));
        user.unblock(byAdminId == null ? null : byAdminId.uuid());
        users.save(user);
    }

    public UserProfile getProfileOrCreate(UserId userId) {
        return profiles.findByUserId(userId)
                .orElseGet(() -> profiles.save(new UserProfile(userId)));
    }

    @Transactional
    public UserProfile updatePreferences(UserId userId,
                                         String language,
                                         UserProfile.Theme theme,
                                         String preferredCurrency,
                                         String timezone,
                                         boolean pushEnabled,
                                         boolean emailNotifEnabled,
                                         boolean marketingOptIn) {
        UserProfile p = getProfileOrCreate(userId);
        p.updatePreferences(language, theme, preferredCurrency, timezone,
                pushEnabled, emailNotifEnabled, marketingOptIn);
        return profiles.save(p);
    }

    @Transactional
    public UserProfile updateContactAddress(UserId userId,
                                            String addressLine, String addressCity, String addressCountry,
                                            String billingTaxId, String profilePictureUrl, String bio) {
        UserProfile p = getProfileOrCreate(userId);
        p.updateContactAddress(addressLine, addressCity, addressCountry, billingTaxId, profilePictureUrl, bio);
        return profiles.save(p);
    }

    @Transactional
    public KycProviderPort.KycSubmissionResult submitKycDocument(UserId userId,
                                                                 DocumentNumber document,
                                                                 byte[] payload) {
        User u = users.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId.uuid()));
        u.assertCanOperate("kyc submit");
        u.addKycDocument(document);
        KycProviderPort.KycSubmissionResult result = kyc.submit(userId, document, payload);
        if (result.status() == KycProviderPort.KycSubmissionResult.Status.APPROVED) {
            try {
                u.upgradeKycLevel(result.achievedLevel(), null);
            } catch (Exception okDowngradeIgnored) {
                // Ya está en nivel >= achieved; sandbox siempre sube.
            }
            users.save(u);
        }
        return result;
    }

    public List<KycProviderPort.KycSubmissionResult> listKyc(UserId userId) {
        return kyc.listByUser(userId);
    }

    @Transactional
    public void upgradeKycLevelByAdmin(UserId userId, KycLevel target, UserId byAdminId) {
        if (byAdminId == null) throw new IllegalArgumentException("byAdminId requerido");
        User u = users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId.uuid()));
        User admin = users.findById(byAdminId).orElseThrow(() -> new UserNotFoundException(byAdminId.uuid()));
        if (!admin.hasRole("ROLE_ADMIN"))
            throw new com.micropay.backend.domain.exceptions.BusinessRuleViolationException(
                    "NOT_AUTHORIZED", "Solo ADMIN puede hacer upgrade manual KYC");
        u.upgradeKycLevel(target, byAdminId.uuid());
        users.save(u);
    }

    public TierLimitsPort.TierLimit getCurrentTierLimit(UserId userId, String countryISO2,
                                                        com.micropay.backend.domain.valueobjects.Currency currency) {
        User u = users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId.uuid()));
        return tierLimits.findFor(u.kycLevel(), countryISO2 == null ? "CO" : countryISO2, currency)
                .orElse(null);
    }
}
