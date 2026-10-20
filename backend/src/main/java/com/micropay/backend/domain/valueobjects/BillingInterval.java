package com.micropay.backend.domain.valueobjects;

import com.micropay.backend.domain.exceptions.BusinessRuleViolationException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Intervalo de facturación de un Plan (Requirement 22.1).
 * Define cómo avanzar la fecha del próximo cobro (MONTH/YEAR usan calendario, no duraciones fijas).
 */
public enum BillingInterval {

    DAY("Diario"),
    WEEK("Semanal"),
    MONTH("Mensual"),
    YEAR("Anual");

    private final String description;

    BillingInterval(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    /**
     * Avanza {@code from} en {@code count} intervalos (aritmética de calendario en UTC).
     *
     * @param from  instante base (no nulo)
     * @param count número de intervalos (> 0)
     */
    public Instant advance(Instant from, int count) {
        if (from == null) {
            throw new BusinessRuleViolationException("BILLING_INTERVAL_FROM_NULL",
                    "La fecha base no puede ser nula");
        }
        if (count < 1) {
            throw new BusinessRuleViolationException("BILLING_INTERVAL_COUNT_INVALID",
                    "intervalCount debe ser >= 1, recibió " + count);
        }
        ZonedDateTime z = from.atZone(ZoneOffset.UTC);
        Instant next = switch (this) {
            case DAY -> z.plusDays(count).toInstant();
            case WEEK -> z.plusWeeks(count).toInstant();
            case MONTH -> z.plusMonths(count).toInstant();
            case YEAR -> z.plusYears(count).toInstant();
        };
        if (!next.isAfter(from)) {
            throw new BusinessRuleViolationException("BILLING_INTERVAL_NOT_ADVANCING",
                    "El siguiente cobro %s no es posterior a %s".formatted(next, from));
        }
        return next;
    }
}
