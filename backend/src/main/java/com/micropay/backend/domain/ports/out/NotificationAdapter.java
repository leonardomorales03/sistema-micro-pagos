package com.micropay.backend.domain.ports.out;

/**
 * Port OUT — Notificaciones (EMAIL, PUSH/FMC).
 * Stub en local; implementación real en infra con JavaMail + FCM Admin.
 */
public interface NotificationAdapter {

    enum Channel { EMAIL, PUSH }

    record NotificationRequest(com.micropay.backend.domain.valueobjects.UserId userId,
                                Channel channel,
                                String template,
                                java.util.Map<String,Object> payload) {}

    void send(NotificationRequest req);
}
