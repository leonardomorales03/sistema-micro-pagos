package com.micropay.backend.infrastructure.jobs;

import com.micropay.backend.application.services.ReportService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Job diario de reconciliación (T25 / Requisito 28.3).
 * <p>
 * Ejecuta la reconciliación del día anterior para cada proveedor configurado en
 * {@code micropay.reconciliation.providers}. Protegido con ShedLock para que sólo
 * una instancia ejecute el job por ventana. Cron en {@code micropay.reconciliation.cron}.
 */
@Component
public class ReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

    private final ReportService reports;

    @Value("${micropay.reconciliation.enabled:true}")
    private boolean enabled;

    @Value("${micropay.reconciliation.providers:stripe}")
    private String providers;

    public ReconciliationJob(ReportService reports) {
        this.reports = reports;
    }

    @Scheduled(cron = "${micropay.reconciliation.cron:0 0 2 * * *}")
    @SchedulerLock(name = "reconciliation_job", lockAtMostFor = "30m", lockAtLeastFor = "1m")
    public void runDailyReconciliation() {
        if (!enabled) {
            return;
        }
        try {
            LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
            Instant periodStart = yesterday.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant periodEnd = yesterday.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

            for (String provider : providers.split(",")) {
                String p = provider.trim();
                if (p.isEmpty()) continue;
                try {
                    reports.runReconciliation(p, periodStart, periodEnd, null);
                } catch (Exception ex) {
                    log.error("[SCHEDULER] reconciliation_job provider={} falló: {}", p, ex.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[SCHEDULER] reconciliation_job falló: {}", e.getMessage(), e);
        }
    }
}
