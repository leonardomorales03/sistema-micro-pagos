package com.micropay.backend.domain.ports.out;

import com.micropay.backend.domain.entities.UserNotification;
import com.micropay.backend.domain.valueobjects.UserId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto OUT — persistencia del log de notificaciones (Requisito 25.3).
 */
public interface NotificationRepository {

    UserNotification save(UserNotification notification);

    Optional<UserNotification> findById(UUID id);

    /** Notificaciones del usuario, más recientes primero. */
    List<UserNotification> listByUser(UserId userId, int page, int pageSize);

    /** Contador de no leídas (badge). */
    long countUnread(UserId userId);

    /** Marca todas las no leídas como leídas; devuelve cuántas se actualizaron. */
    int markAllRead(UserId userId);
}
