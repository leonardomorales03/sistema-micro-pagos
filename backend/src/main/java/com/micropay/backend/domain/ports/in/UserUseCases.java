package com.micropay.backend.domain.ports.in;

import com.micropay.backend.domain.entities.User;
import com.micropay.backend.domain.valueobjects.Email;
import com.micropay.backend.domain.valueobjects.UserId;

import java.util.List;
import java.util.Optional;

/**
 * UseCases IN (entrada) relacionados a Usuarios.
 * Implementado en Application Layer → com.micropay.backend.application.usecases.*
 */
public interface UserUseCases {

    User register(Email email, String rawPassword, String fullName, UserId referredBy);

    Optional<User> findById(UserId id);

    Optional<User> findByEmail(Email email);

    Optional<User> findByReferralCode(String referralCode);

    List<User> listUsers(int page, int pageSize);

    void verifyEmail(UserId userId);

    void blockUser(UserId userId, String reason, UserId byAdminId);

    void unblockUser(UserId userId, UserId byAdminId);
}
