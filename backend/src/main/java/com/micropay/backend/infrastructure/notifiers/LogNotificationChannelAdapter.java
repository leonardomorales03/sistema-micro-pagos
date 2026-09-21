package com.micropay.backend.infrastructure.notifiers;

import com.micropay.backend.domain.ports.out.NotificationAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementación Canal Notificaciones "log-based (logback) — stub local/dev;
 * En producción se sustituye por:
 *   - SMTP Mailgun / SES para EMAIL
 *   - FCM Admin SDK para PUSH
 * <p>
 * Actualmente SOLO loguea; NO envía emails reales — ideal para tests locales.
 */
@Component
public class LogNotificationChannelAdapter implements NotificationAdapter {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT_NOTIFICATIONS");

    @Override
    public void send(NotificationRequest req) {
        if (req == null) return;
        audit.info("⧗ NOTIF channel={} userId={} template={} payload={}",
                req.channel(),
                req.userId() != null ? req.userId().uuid() : null,
                req.template(),
                req.payload());
    }
}
