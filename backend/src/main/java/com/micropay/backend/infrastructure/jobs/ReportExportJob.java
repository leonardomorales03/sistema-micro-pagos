package com.micropay.backend.infrastructure.jobs;

import com.micropay.backend.application.services.ReportService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Poller de exportaciones CSV asíncronas (T25 / Requisito 28.1).
 * <p>
 * Procesa los jobs en estado QUEUED y genera el CSV en background. Protegido con
 * ShedLock (patrón de los schedulers de T22) para que sólo una instancia lo ejecute
 * por ventana. Cron configurable en {@code micropay.reports.poll-cron}.
 */
@Component
public class ReportExportJob {

    private static final Logger log = LoggerFactory.getLogger(ReportExportJob.class);

    private final ReportService reports;

    public ReportExportJob(ReportService reports) {
        this.reports = reports;
    }

    @Scheduled(cron = "${micropay.reports.poll-cron:0 */1 * * * *}")
    @SchedulerLock(name = "report_export_job", lockAtMostFor = "5m", lockAtLeastFor = "10s")
    public void processQueuedExports() {
        try {
            int processed = reports.processQueuedExports();
            if (processed > 0) {
                log.info("[SCHEDULER] report_export_job → {} export(s) procesado(s)", processed);
            }
        } catch (Exception e) {
            // Nunca propagar: un fallo del job no debe tumbar el scheduler.
            log.error("[SCHEDULER] report_export_job falló: {}", e.getMessage(), e);
        }
    }
}
