package com.micropay.backend.application.services;

import com.micropay.backend.domain.entities.UserNotification;
import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;
import com.micropay.backend.domain.ports.out.NotificationRepository;
import com.micropay.backend.domain.valueobjects.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Casos de uso de notificaciones del usuario (Requisito 25.3 / T22).
 * La escritura del log ocurre en {@link com.micropay.backend.infrastructure.notifiers.PersistingNotificationAdapter}.
 */
@Service
@Transactional(readOnly = true)
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    public List<UserNotification> list(UserId userId, int page, int pageSize) {
        return notifications.listByUser(userId, page, pageSize);
    }

    public long unreadCount(UserId userId) {
        return notifications.countUnread(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserNotification markRead(UserId userId, UUID id) {
        UserNotification n = getOwned(userId, id);
        n.markRead();
        UserNotification saved = notifications.save(n);
        log.debug("[NOTIF] Marcada leída id={} user={}", id, userId);
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public int markAllRead(UserId userId) {
        int updated = notifications.markAllRead(userId);
        log.info("[NOTIF] Marcadas {} notificaciones leídas user={}", updated, userId);
        return updated;
    }

    private UserNotification getOwned(UserId userId, UUID id) {
        UserNotification n = notifications.findById(id)
                .orElseThrow(() -> new BusinessRuleViolationException("NOTIFICATION_NOT_FOUND",
                        "Notificación " + id + " no encontrada"));
        if (!n.userId().equals(userId)) {
            throw new BusinessRuleViolationException("NOTIFICATION_NOT_OWNED_BY_USER",
                    "La notificación %s no pertenece al usuario %s".formatted(id, userId));
        }
        return n;
    }
}
