package com.micropay.backend.domain.valueobjects;

/**
 * Canal de entrega de una notificación (Requisito 25.1).
 * El log persistido siempre registra una fila; IN_APP es el canal por defecto,
 * EMAIL se envía además por SMTP cuando el usuario lo tiene habilitado.
 */
public enum NotificationChannel {
    IN_APP,
    EMAIL,
    SMS,
    PUSH
}
