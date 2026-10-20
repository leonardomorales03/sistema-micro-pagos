package com.micropay.backend.domain.valueobjects;

import java.util.Collection;

/**
 * Eventos de negocio publicables como webhook a comercios (Requisito 25.4).
 *
 * La máscara de suscripción del comercio ({@code merchant_webhook_configs.subscribed_events})
 * compara contra {@link #name()} o contra el comodín {@code "*"}.
 */
public enum WebhookEventType {
    PAYMENT_REQUEST_CREATED,
    PAYMENT_REQUEST_PAID,
    PAYMENT_REQUEST_EXPIRED,
    TOPUP_SUCCEEDED,
    TOPUP_FAILED,
    WITHDRAWAL_COMPLETED,
    WITHDRAWAL_FAILED,
    SUBSCRIPTION_CHARGED,
    SUBSCRIPTION_FAILED,
    REFUND_COMPLETED;

    /** Comodín que suscribe a todos los eventos. */
    public static final String WILDCARD = "*";

    /** ¿La máscara {@code subscribedEvents} cubre este evento? */
    public static boolean covers(Collection<String> subscribedEvents, String eventType) {
        if (eventType == null || subscribedEvents == null) return false;
        for (String s : subscribedEvents) {
            if (s == null) continue;
            String v = s.trim();
            if (WILDCARD.equals(v) || v.equalsIgnoreCase(eventType)) return true;
        }
        return false;
    }

    /** ¿Existe el evento indicado? */
    public static boolean isKnown(String eventType) {
        if (eventType == null || eventType.isBlank()) return false;
        for (WebhookEventType t : values()) {
            if (t.name().equalsIgnoreCase(eventType.trim())) return true;
        }
        return false;
    }

    /** Normaliza un nombre de evento al valor del enum (case-insensitive). */
    public static WebhookEventType parse(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType es requerido");
        }
        for (WebhookEventType t : values()) {
            if (t.name().equalsIgnoreCase(eventType.trim())) return t;
        }
        throw new IllegalArgumentException("Evento de webhook desconocido: " + eventType);
    }
}
