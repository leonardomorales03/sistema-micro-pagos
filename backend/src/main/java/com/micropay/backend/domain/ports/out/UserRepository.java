package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.entities.User;
import com.micropay.backend.domain.valueobjects.Email;
import com.micropay.backend.domain.valueobjects.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Port OUT — Repositorio Usuarios.
 * Implementado por Infrastructure Adapter → PostgresUserRepository
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UserId id);

    Optional<User> findByEmail(Email email);

    Optional<User> findByReferralCode(String referralCode);

    List<User> list(int page, int pageSize);

    boolean existsByEmail(Email email);
}
