package org.example.dumanagementbackend.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.example.dumanagementbackend.entity.NotificationJob;
import org.example.dumanagementbackend.entity.enums.SystemLogCategory;
import org.example.dumanagementbackend.entity.enums.SystemLogSeverity;
import org.example.dumanagementbackend.entity.enums.SystemLogStatus;
import org.example.dumanagementbackend.logging.SystemLogContext;
import org.example.dumanagementbackend.logging.SystemLogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {

    private final NotificationJobService notificationJobService;
    private final NotificationService notificationService;
    private final SystemLogService systemLogService;

    @Scheduled(fixedDelayString = "${app.notification.scheduler.poll-ms:30000}")
    public void dispatchDueJobs() {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        List<NotificationJob> enabledJobs = notificationJobService.getEnabledJobsForDispatch();

        for (NotificationJob job : enabledJobs) {
            if (!notificationJobService.shouldRun(job, now)) {
                continue;
            }

            long startedAt = System.nanoTime();
            int sentCount = 0;
            try {
                sentCount = switch (job.getCode()) {
                    case NotificationJobService.CRON_BIRTHDAY_ANNIVERSARY -> notificationService.runBirthdayAnniversaryJob();
                    case NotificationJobService.CRON_EVENT_REMINDER -> notificationService.runEventReminderJob();
                    case NotificationJobService.CRON_SURVEY_REMINDER -> notificationService.runSurveyReminderJob();
                    case NotificationJobService.CRON_ORDER_SESSION_CREATOR_REMINDER -> notificationService.runOrderSessionCreatorReminderJob();
                    default -> 0;
                };
                if (sentCount > 0) {
                    log.info("Notification job {} executed successfully. Sent {} notification(s)", job.getCode(), sentCount);
                    logTask(job.getCode(), sentCount, SystemLogStatus.SUCCESS, SystemLogSeverity.INFO, startedAt, null);
                } else {
                    log.debug("Notification job {} checked with no notifications to send", job.getCode());
                }
            } catch (Exception ex) {
                log.error("Notification job {} failed: {}", job.getCode(), ex.getMessage(), ex);
                logTask(job.getCode(), sentCount, SystemLogStatus.FAILED, SystemLogSeverity.ERROR, startedAt, ex);
            } finally {
                notificationJobService.markJobRun(job.getCode(), now);
            }
        }
    }

    private void logTask(
            String jobCode,
            int sentCount,
            SystemLogStatus status,
            SystemLogSeverity severity,
            long startedAt,
            Throwable failure
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("jobCode", jobCode);
        details.put("sentCount", sentCount);
        details.put("trigger", "scheduled");

        systemLogService.log(new SystemLogCreateRequest(
                SystemLogCategory.TASK,
                severity,
                status,
                "NOTIFICATION_JOB",
                "NotificationScheduler",
                SystemLogContext.getActorUsername(),
                SystemLogContext.getCorrelationId(),
                "NotificationJob",
                jobCode,
                (System.nanoTime() - startedAt) / 1_000_000,
                "Notification job " + jobCode + " " + status.name().toLowerCase(),
                details,
                failure != null ? failure.getClass().getName() : null,
                SystemLogSanitizer.stackTrace(failure)
        ));
    }
}
