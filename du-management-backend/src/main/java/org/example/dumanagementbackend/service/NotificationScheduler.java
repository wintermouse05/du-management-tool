package org.example.dumanagementbackend.service;

import java.time.LocalDateTime;
import java.util.List;
import org.example.dumanagementbackend.entity.NotificationJob;
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

    @Scheduled(fixedDelayString = "${app.notification.scheduler.poll-ms:30000}")
    public void dispatchDueJobs() {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        List<NotificationJob> enabledJobs = notificationJobService.getEnabledJobsForDispatch();

        for (NotificationJob job : enabledJobs) {
            if (!notificationJobService.shouldRun(job, now)) {
                continue;
            }

            try {
                int sentCount = switch (job.getCode()) {
                    case NotificationJobService.CRON_BIRTHDAY_ANNIVERSARY -> notificationService.runBirthdayAnniversaryJob();
                    case NotificationJobService.CRON_EVENT_REMINDER -> notificationService.runEventReminderJob();
                    case NotificationJobService.CRON_SURVEY_REMINDER -> notificationService.runSurveyReminderJob();
                    default -> 0;
                };
                log.info("Notification job {} executed successfully. Sent {} notification(s)", job.getCode(), sentCount);
            } catch (Exception ex) {
                log.error("Notification job {} failed: {}", job.getCode(), ex.getMessage(), ex);
            } finally {
                notificationJobService.markJobRun(job.getCode(), now);
            }
        }
    }
}