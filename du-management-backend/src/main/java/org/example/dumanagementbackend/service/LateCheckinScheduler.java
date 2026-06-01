package org.example.dumanagementbackend.service;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.example.dumanagementbackend.entity.enums.SystemLogCategory;
import org.example.dumanagementbackend.entity.enums.SystemLogSeverity;
import org.example.dumanagementbackend.entity.enums.SystemLogStatus;
import org.example.dumanagementbackend.logging.SystemLogContext;
import org.example.dumanagementbackend.logging.SystemLogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "chatops.enabled", havingValue = "true")
public class LateCheckinScheduler {

    private static final Logger log = LoggerFactory.getLogger(LateCheckinScheduler.class);

    private final LateRecordService lateRecordService;
    private final ChatopsService chatopsService;
    private final SystemLogService systemLogService;

    @Scheduled(cron = "0 05 10 * * MON-FRI", zone = "Asia/Ho_Chi_Minh")
    public void scheduledFetch() {
        long startedAt = System.nanoTime();
        log.info("Running scheduled late check-in fetch...");
        int saved = 0;
        try {
            saved = lateRecordService.fetchLateCheckinsFromChat(chatopsService.getInputChannelId(), LocalTime.of(10, 0));
            log.info("Scheduled late check-in fetch completed. {} records saved.", saved);
            logFetch(saved, SystemLogStatus.SUCCESS, SystemLogSeverity.INFO, startedAt, null);
        } catch (Exception ex) {
            log.error("Scheduled late check-in fetch failed: {}", ex.getMessage(), ex);
            logFetch(saved, SystemLogStatus.FAILED, SystemLogSeverity.ERROR, startedAt, ex);
        }
    }

    private void logFetch(int saved, SystemLogStatus status, SystemLogSeverity severity, long startedAt, Throwable failure) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("savedRecords", saved);
        details.put("trigger", "scheduled");

        systemLogService.log(new SystemLogCreateRequest(
                SystemLogCategory.TASK,
                severity,
                status,
                "LATE_CHECKIN_FETCH",
                "LateCheckinScheduler",
                SystemLogContext.getActorUsername(),
                SystemLogContext.getCorrelationId(),
                "ScheduledTask",
                "late-checkin-fetch",
                (System.nanoTime() - startedAt) / 1_000_000,
                "Scheduled late check-in fetch " + status.name().toLowerCase(),
                details,
                failure != null ? failure.getClass().getName() : null,
                SystemLogSanitizer.stackTrace(failure)
        ));
    }
}
