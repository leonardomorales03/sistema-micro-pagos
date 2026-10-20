package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.entities.Session;
import com.micropay.backend.domain.valueobjects.UserId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port OUT — Repositorio Sessions Refresh Token JWT.
 * Habilita revocación granular (logout de device específico).
 */
public interface SessionRepository {

    Session save(Session session);

    Optional<Session> findByRefreshTokenHash(String refreshTokenHash);

    Optional<Session> findById(UUID sessionId);

    List<Session> listByUser(UserId userId);

    /** Revoca TODAS las sesiones de un usuario por motivo. */
    int revokeAllForUser(UserId userId, String reason);
}
