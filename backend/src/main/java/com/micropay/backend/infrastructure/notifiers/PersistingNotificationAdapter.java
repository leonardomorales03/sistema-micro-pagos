package com.micropay.backend.infrastructure.notifiers;

import com.micropay.backend.domain.entities.UserNotification;
import com.micropay.backend.domain.ports.out.NotificationAdapter;
import com.micropay.backend.domain.ports.out.NotificationRepository;
import com.micropay.backend.domain.valueobjects.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adaptador por defecto de {@link NotificationAdapter} (T22 / Requisito 25).
 *
 *  1. Persiste SIEMPRE una fila en `user_notifications` (canal IN_APP) → log append-only
 *     consultable por el usuario (Requisito 25.3).
 *  2. Si el canal solicitado es EMAIL delega en {@link EmailNotificationAdapter}.
 *
 * Es best-effort: un fallo de persistencia/envío se loguea y NUNCA se propaga
 * (las operaciones de negocio que notifican ya están confirmadas).
 */
@Component
public class PersistingNotificationAdapter implements NotificationAdapter {

    private static final Logger log = LoggerFactory.getLogger(PersistingNotificationAdapter.class);
    private static final Logger audit = LoggerFactory.getLogger("AUDIT_NOTIFICATIONS");

    private final NotificationRepository notifications;
    private final EmailNotificationAdapter email;

    public PersistingNotificationAdapter(NotificationRepository notifications,
                                         EmailNotificationAdapter email) {
        this.notifications = notifications;
        this.email = email;
    }

    @Override
    public void send(NotificationRequest req) {
        if (req == null || req.userId() == null) return;

        audit.info("⧗ NOTIF channel={} userId={} template={} payload={}",
                req.channel(), req.userId().uuid(), req.template(), req.payload());

        try {
            notifications.save(UserNotification.sent(
                    req.userId(), req.template(), NotificationChannel.IN_APP, req.payload()));
        } catch (Exception e) {
            log.warn("No se pudo persistir la notificación {} de user {}: {}",
                    req.template(), req.userId(), e.getMessage());
        }

        if (req.channel() == Channel.EMAIL) {
            email.send(req.userId(), req.template(), req.payload());
        }
    }
}
