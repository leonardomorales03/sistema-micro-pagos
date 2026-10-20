package com.micropay.backend.domain.valueobjects;

/**
 * Estado de entrega/lectura de una notificación (Requisito 25.3).
 * QUEUED → pendiente de envío; SENT → entregada al canal; FAILED → error de entrega;
 * READ → el usuario la marcó como leída.
 */
public enum NotificationStatus {
    QUEUED,
    SENT,
    FAILED,
    READ
}
