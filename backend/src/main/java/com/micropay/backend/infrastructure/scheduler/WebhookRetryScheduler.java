package com.micropay.backend.infrastructure.scheduler;

import com.micropay.backend.application.services.WebhookService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler de reintentos de webhooks (T22 / Requisito 25.4).
 * Protegido con ShedLock para que sólo una instancia procese la cola por ventana.
 * Backoff configurable en {@code micropay.webhooks.retry-delays} (1m/5m/15m/1h/6h/24h).
 */
@Component
public class WebhookRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(WebhookRetryScheduler.class);

    private final WebhookService webhooks;

    public WebhookRetryScheduler(WebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @Scheduled(cron = "${micropay.webhooks.retry-cron:0 */1 * * * *}")
    @SchedulerLock(name = "webhook_retry_job", lockAtMostFor = "5m", lockAtLeastFor = "10s")
    public void retryFailedDeliveries() {
        try {
            int processed = webhooks.processDueRetries();
            if (processed > 0) {
                log.info("[SCHEDULER] webhook_retry_job → {} entregas reintentadas", processed);
            }
        } catch (Exception e) {
            // Nunca propagar: un fallo del job no debe tumbar el scheduler.
            log.error("[SCHEDULER] webhook_retry_job falló: {}", e.getMessage(), e);
        }
    }
}
