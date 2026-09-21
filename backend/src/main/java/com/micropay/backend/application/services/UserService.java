package com.micropay.backend.application.services;

import com.micropay.backend.domain.entities.User;
import com.micropay.backend.domain.exceptions.UserNotFoundException;
import com.micropay.backend.domain.ports.in.UserUseCases;
import com.micropay.backend.domain.ports.out.NotificationAdapter;
import com.micropay.backend.domain.ports.out.UserRepository;
import com.micropay.backend.domain.valueobjects.Email;
import com.micropay.backend.domain.valueobjects.UserId;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserService implements UserUseCases {

    private final UserRepository users;
    private final NotificationAdapter notifications;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, NotificationAdapter notifications, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.notifications = notifications;
        this.passwordEncoder = passwordEncoder;
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
        if (referredBy != null && !users.existsByEmail(new Email("_validate_"+referredBy.uuid()))) {
            if (!users.findById(referredBy).isPresent()) {
                referredBy = null;
            }
        }
        String bcryptHash = passwordEncoder.encode(rawPassword);
        User created = User.registerNew(email, bcryptHash, fullName, referredBy);
        User saved = users.save(created);

        // Notificación email bienvenida (stub logs)
        notifications.send(new NotificationAdapter.NotificationRequest(
                saved.id(),
                NotificationAdapter.Channel.EMAIL,
                "WELCOME_VERIFY_EMAIL",
                Map.of(
                        "email", saved.email().value(),
                        "full_name", saved.fullName(),
                        "referral_code", saved.referralCode()
                )
        ));
        return saved;
    }

    @Override
    public Optional<User> findById(UserId id) { return users.findById(id); }

    @Override
    public Optional<User> findByEmail(Email email) { return users.findByEmail(email); }

    @Override
    public Optional<User> findByReferralCode(String referralCode) {
        return users.findByReferralCode(referralCode);
    }

    @Override
    public List<User> listUsers(int page, int pageSize) {
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
}
