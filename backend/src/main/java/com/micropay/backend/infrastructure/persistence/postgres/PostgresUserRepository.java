package com.micropay.backend.infrastructure.persistence.postgres;

import com.micropay.backend.domain.entities.User;
import com.micropay.backend.domain.ports.out.UserRepository;
import com.micropay.backend.domain.valueobjects.Email;
import com.micropay.backend.domain.valueobjects.KycLevel;
import com.micropay.backend.domain.valueobjects.UserId;
import com.micropay.backend.infrastructure.persistence.jpa.entities.UserJpa;
import com.micropay.backend.infrastructure.persistence.jpa.repositories.UserJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PostgresUserRepository implements UserRepository {

    private final UserJpaRepository jpa;

    public PostgresUserRepository(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public User save(User user) {
        UserJpa jpaEntity = toJpa(user);
        UserJpa saved = jpa.save(jpaEntity);
        return toDomain(saved);
    }

    @Override
    public Optional<User> findById(UserId id) {
        return jpa.findById(id.uuid()).map(this::toDomain);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return jpa.findByEmailIgnoreCase(email.value()).map(this::toDomain);
    }

    @Override
    public Optional<User> findByReferralCode(String referralCode) {
        return jpa.findByReferralCode(referralCode).map(this::toDomain);
    }

    @Override
    public List<User> list(int page, int pageSize) {
        return jpa.findAllBy(PageRequest.of(page, pageSize))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public boolean existsByEmail(Email email) {
        return jpa.existsByEmailIgnoreCase(email.value());
    }

    // ──────────────── MAPPERS Domain ↔ JPA ────────────────

    User toDomain(UserJpa j) {
        if (j == null) return null;
        User.Builder b = User.builder()
                .id(UserId.from(j.getId()))
                .email(new Email(j.getEmail()))
                .fullName(j.getFullName())
                .phoneNumber(j.getPhoneNumber())
                .passwordHash(j.getPasswordHash())
                .kycLevel(KycLevel.valueOf(j.getKycLevel().name()))
                .status(User.Status.valueOf(j.getStatus().name()))
                .emailVerified(j.isEmailVerified())
                .referralCode(j.getReferralCode())
                .createdAt(j.getCreatedAt())
                .updatedAt(j.getUpdatedAt());
        if (j.getReferredBy() != null) b.referredBy(UserId.from(j.getReferredBy()));
        if (j.getRoles() != null) j.getRoles().forEach(b::addRole);
        return b.build();
    }

    UserJpa toJpa(User d) {
        if (d == null) return null;
        UserJpa j = new UserJpa();
        j.setId(d.id().uuid());
        j.setEmail(d.email().value());
        j.setFullName(d.fullName());
        j.setPhoneNumber(d.phoneNumber());
        j.setPasswordHash(getPasswordHashViaReflection(d));
        j.setKycLevel(UserJpa.KycLevel.valueOf(d.kycLevel().name()));
        j.setStatus(UserJpa.UserStatus.valueOf(d.status().name()));
        j.setEmailVerified(d.emailVerified());
        j.setReferralCode(d.referralCode());
        if (d.referredBy() != null) j.setReferredBy(d.referredBy().uuid());
        j.setRoles(d.roles());
        j.setCreatedAt(d.createdAt());
        j.setUpdatedAt(d.updatedAt());
        return j;
    }

    private String getPasswordHashViaReflection(User user) {
        try {
            java.lang.reflect.Field f = User.class.getDeclaredField("passwordHash");
            f.setAccessible(true);
            return (String) f.get(user);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo leer passwordHash de User", e);
        }
    }
}
