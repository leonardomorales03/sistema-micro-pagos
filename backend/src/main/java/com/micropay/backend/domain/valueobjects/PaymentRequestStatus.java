package com.micropay.backend.domain.valueobjects;

/**
 * Ciclo de vida de un PaymentRequest (link/QR de comercio).
 *
 * Los valores coinciden 1:1 con el CHECK de la tabla `payment_requests` (V1), ampliado
 * en V5 para incluir REFUNDED:
 *
 *   PENDING ──(pay)──> PAID ──(refund)──> REFUNDED
 *      │
 *      ├──(cancel)──> CANCELLED
 *      └──(expira)──> EXPIRED
 *
 * PENDING es el estado "abierto" (awaiting payment) — equivale a OPEN del diseño.
 */
public enum PaymentRequestStatus {
    PENDING,
    PAID,
    CANCELLED,
    EXPIRED,
    REFUNDED
}
