package com.micropay.backend.domain.valueobjects;

/**
 * Estado de una Subscription (design.md / Requirement 22).
 *   TRIALING → ACTIVE → (PAST_DUE) → CANCELED
 *                  ↕ PAUSED
 */
public enum SubscriptionStatus {
    TRIALING,
    ACTIVE,
    PAST_DUE,
    CANCELED,
    PAUSED
}
