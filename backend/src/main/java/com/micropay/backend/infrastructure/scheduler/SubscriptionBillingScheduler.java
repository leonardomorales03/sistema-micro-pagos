package com.micropay.backend.infrastructure.scheduler;

import com.micropay.backend.application.services.SubscriptionBillingService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler de facturación de suscripciones (T20 / Requirement 22.3).
 * Protegido con ShedLock para que sólo una instancia ejecute el job por ventana.
 * Por defecto corre cada hora; búsqueda de {@code next_billing_date ≤ now}.
 */
@Component
public class SubscriptionBillingScheduler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionBillingScheduler.class);

    private final SubscriptionBillingService billing;

    public SubscriptionBillingScheduler(SubscriptionBillingService billing) {
        this.billing = billing;
    }

    @Scheduled(cron = "${micropay.subscriptions.billing.cron:0 5 * * * *}")
    @SchedulerLock(name = "subscription_billing_job", lockAtMostFor = "10m", lockAtLeastFor = "1m")
    public void runSubscriptionBilling() {
        try {
            SubscriptionBillingService.BillingRunSummary summary = billing.runBillingCycle();
            log.info("[SCHEDULER] subscription_billing_job → {}", summary);
        } catch (Exception e) {
            // Nunca propagar: un fallo del job no debe tumbar el scheduler.
            log.error("[SCHEDULER] subscription_billing_job falló: {}", e.getMessage(), e);
        }
    }
}
