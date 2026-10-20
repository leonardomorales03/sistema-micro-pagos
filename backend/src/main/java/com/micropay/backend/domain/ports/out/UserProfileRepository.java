package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.entities.UserProfile;
import com.micropay.backend.domain.valueobjects.UserId;

import java.util.Optional;

/**
 * UserProfile repository (1:1 con user). 1 User → 1 UserProfile.
 * Siempre existe: signup crea user_profiles automáticamente (trigger INSERT o service).
 */
public interface UserProfileRepository {
    UserProfile save(UserProfile p);
    Optional<UserProfile> findByUserId(UserId u);
}
