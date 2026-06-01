package org.example.dumanagementbackend.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.example.dumanagementbackend.entity.NotificationSchedule;
import org.example.dumanagementbackend.entity.enums.NotificationScheduleType;
import org.example.dumanagementbackend.entity.enums.SystemLogCategory;
import org.example.dumanagementbackend.entity.enums.SystemLogSeverity;
import org.example.dumanagementbackend.entity.enums.SystemLogStatus;
import org.example.dumanagementbackend.logging.SystemLogContext;
import org.example.dumanagementbackend.logging.SystemLogSanitizer;
import org.example.dumanagementbackend.repository.NotificationJobRepository;
import org.example.dumanagementbackend.repository.NotificationScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "chatops.enabled", havingValue = "true")
public class ScheduleManager {

    private static final Logger log = LoggerFactory.getLogger(ScheduleManager.class);
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final TaskScheduler taskScheduler;
    private final NotificationScheduleRepository scheduleRepository;
    private final NotificationJobRepository notificationJobRepository;
    private final ChatopsNotificationService chatopsNotificationService;
    private final SystemLogService systemLogService;

    private final Map<NotificationScheduleType, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    public synchronized void updateSchedule(NotificationScheduleType type) {
        switch (type) {
            case BIRTHDAY -> rescheduleBirthdayTask();
            case ANNIVERSARY -> rescheduleAnniversaryTask();
            case LATE -> scheduleLateNotificationTask();
            case EVENT -> rescheduleEventTask();
            case LEADERBOARD -> scheduleLeaderboardTask();
        }
    }

    public synchronized void cancelSchedule(NotificationScheduleType type) {
        ScheduledFuture<?> task = tasks.remove(type);
        if (task != null && !task.isCancelled()) {
            task.cancel(false);
            log.info("Cancelled schedule for type: {}", type);
        }
    }

    // ---- birthday ----

    public synchronized void rescheduleBirthdayTask() {
        if (isUnifiedNotificationJobEnabled(NotificationScheduleType.BIRTHDAY)) {
            cancelSchedule(NotificationScheduleType.BIRTHDAY);
            log.info("Skipped legacy birthday schedule because {} is enabled", NotificationJobService.CRON_BIRTHDAY_ANNIVERSARY);
            return;
        }

        NotificationSchedule schedule = scheduleRepository.findByType(NotificationScheduleType.BIRTHDAY)
                .orElse(null);
        if (schedule == null || !schedule.isEnabled()) {
            cancelSchedule(NotificationScheduleType.BIRTHDAY);
            return;
        }

        cancelSchedule(NotificationScheduleType.BIRTHDAY);

        LocalTime sendTime = schedule.getSendTime() != null ? schedule.getSendTime() : LocalTime.of(9, 5);
        ZonedDateTime now = ZonedDateTime.now(VIETNAM_ZONE);
        ZonedDateTime firstRun = now.withHour(sendTime.getHour()).withMinute(sendTime.getMinute()).withSecond(0);
        if (firstRun.isBefore(now)) firstRun = firstRun.plusDays(1);

        long oneDay = Duration.ofDays(1).toMillis();
        ScheduledFuture<?> task = taskScheduler.scheduleAtFixedRate(() -> {
            if (isUnifiedNotificationJobEnabled(NotificationScheduleType.BIRTHDAY)) {
                logLegacySchedule(NotificationScheduleType.BIRTHDAY, "LEGACY_BIRTHDAY_NOTIFICATION", SystemLogStatus.SKIPPED,
                        SystemLogSeverity.INFO, 0, "Unified notification job is enabled", null);
                return;
            }
            runLegacySchedule(NotificationScheduleType.BIRTHDAY, "LEGACY_BIRTHDAY_NOTIFICATION",
                    chatopsNotificationService::sendBirthdayNotification);
        }, Date.from(firstRun.toInstant()), oneDay);
        tasks.put(NotificationScheduleType.BIRTHDAY, task);
        log.info("Scheduled birthday notification at {} daily", sendTime);
    }

    // ---- anniversary ----

    public synchronized void rescheduleAnniversaryTask() {
        if (isUnifiedNotificationJobEnabled(NotificationScheduleType.ANNIVERSARY)) {
            cancelSchedule(NotificationScheduleType.ANNIVERSARY);
            log.info("Skipped legacy anniversary schedule because {} is enabled", NotificationJobService.CRON_BIRTHDAY_ANNIVERSARY);
            return;
        }

        NotificationSchedule schedule = scheduleRepository.findByType(NotificationScheduleType.ANNIVERSARY)
                .orElse(null);
        if (schedule == null || !schedule.isEnabled()) {
            cancelSchedule(NotificationScheduleType.ANNIVERSARY);
            return;
        }

        cancelSchedule(NotificationScheduleType.ANNIVERSARY);

        LocalTime sendTime = schedule.getSendTime() != null ? schedule.getSendTime() : LocalTime.of(9, 10);
        ZonedDateTime now = ZonedDateTime.now(VIETNAM_ZONE);
        ZonedDateTime firstRun = now.withHour(sendTime.getHour()).withMinute(sendTime.getMinute()).withSecond(0);
        if (firstRun.isBefore(now)) firstRun = firstRun.plusDays(1);

        long oneDay = Duration.ofDays(1).toMillis();
        ScheduledFuture<?> task = taskScheduler.scheduleAtFixedRate(() -> {
            if (isUnifiedNotificationJobEnabled(NotificationScheduleType.ANNIVERSARY)) {
                logLegacySchedule(NotificationScheduleType.ANNIVERSARY, "LEGACY_ANNIVERSARY_NOTIFICATION", SystemLogStatus.SKIPPED,
                        SystemLogSeverity.INFO, 0, "Unified notification job is enabled", null);
                return;
            }
            runLegacySchedule(NotificationScheduleType.ANNIVERSARY, "LEGACY_ANNIVERSARY_NOTIFICATION",
                    chatopsNotificationService::sendAnniversaryNotification);
        }, Date.from(firstRun.toInstant()), oneDay);
        tasks.put(NotificationScheduleType.ANNIVERSARY, task);
        log.info("Scheduled anniversary notification at {} daily", sendTime);
    }

    // ---- late (Mon-Fri) ----

    public synchronized void scheduleLateNotificationTask() {
        NotificationSchedule schedule = scheduleRepository.findByType(NotificationScheduleType.LATE)
                .orElse(null);
        if (schedule == null || !schedule.isEnabled()) {
            cancelSchedule(NotificationScheduleType.LATE);
            return;
        }

        cancelSchedule(NotificationScheduleType.LATE);

        LocalTime sendTime = schedule.getSendTime() != null ? schedule.getSendTime() : LocalTime.of(11, 0);
        ZonedDateTime now = ZonedDateTime.now(VIETNAM_ZONE);
        ZonedDateTime firstRun = now.withHour(sendTime.getHour()).withMinute(sendTime.getMinute()).withSecond(0);
        if (firstRun.isBefore(now)) firstRun = firstRun.plusDays(1);

        long oneDay = Duration.ofDays(1).toMillis();
        ScheduledFuture<?> task = taskScheduler.scheduleAtFixedRate(() -> {
            DayOfWeek today = LocalDate.now(VIETNAM_ZONE).getDayOfWeek();
            if (today != DayOfWeek.SATURDAY && today != DayOfWeek.SUNDAY) {
                runLegacySchedule(NotificationScheduleType.LATE, "LEGACY_LATE_PENALTY_NOTIFICATION",
                        chatopsNotificationService::sendLatePenaltyNotification);
            } else {
                logLegacySchedule(NotificationScheduleType.LATE, "LEGACY_LATE_PENALTY_NOTIFICATION", SystemLogStatus.SKIPPED,
                        SystemLogSeverity.INFO, 0, "Weekend", null);
            }
        }, Date.from(firstRun.toInstant()), oneDay);
        tasks.put(NotificationScheduleType.LATE, task);
        log.info("Scheduled late penalty notification at {} Mon-Fri", sendTime);
    }

    // ---- event (Mon-Fri) ----

    public synchronized void rescheduleEventTask() {
        if (isUnifiedNotificationJobEnabled(NotificationScheduleType.EVENT)) {
            cancelSchedule(NotificationScheduleType.EVENT);
            log.info("Skipped legacy event schedule because {} is enabled", NotificationJobService.CRON_EVENT_REMINDER);
            return;
        }

        NotificationSchedule schedule = scheduleRepository.findByType(NotificationScheduleType.EVENT)
                .orElse(null);
        if (schedule == null || !schedule.isEnabled()) {
            cancelSchedule(NotificationScheduleType.EVENT);
            return;
        }

        cancelSchedule(NotificationScheduleType.EVENT);

        LocalTime sendTime = schedule.getSendTime() != null ? schedule.getSendTime() : LocalTime.of(8, 0);
        ZonedDateTime now = ZonedDateTime.now(VIETNAM_ZONE);
        ZonedDateTime firstRun = now.withHour(sendTime.getHour()).withMinute(sendTime.getMinute()).withSecond(0);
        if (firstRun.isBefore(now)) firstRun = firstRun.plusDays(1);

        long oneDay = Duration.ofDays(1).toMillis();
        ScheduledFuture<?> task = taskScheduler.scheduleAtFixedRate(() -> {
            if (isUnifiedNotificationJobEnabled(NotificationScheduleType.EVENT)) {
                logLegacySchedule(NotificationScheduleType.EVENT, "LEGACY_EVENT_NOTIFICATION", SystemLogStatus.SKIPPED,
                        SystemLogSeverity.INFO, 0, "Unified notification job is enabled", null);
                return;
            }
            DayOfWeek today = LocalDate.now(VIETNAM_ZONE).getDayOfWeek();
            if (today != DayOfWeek.SATURDAY && today != DayOfWeek.SUNDAY) {
                runLegacySchedule(NotificationScheduleType.EVENT, "LEGACY_EVENT_NOTIFICATION",
                        chatopsNotificationService::sendEventNotification);
            } else {
                logLegacySchedule(NotificationScheduleType.EVENT, "LEGACY_EVENT_NOTIFICATION", SystemLogStatus.SKIPPED,
                        SystemLogSeverity.INFO, 0, "Weekend", null);
            }
        }, Date.from(firstRun.toInstant()), oneDay);
        tasks.put(NotificationScheduleType.EVENT, task);
        log.info("Scheduled event notification at {} Mon-Fri", sendTime);
    }

    // ---- leaderboard (Friday) ----

    public synchronized void scheduleLeaderboardTask() {
        NotificationSchedule schedule = scheduleRepository.findByType(NotificationScheduleType.LEADERBOARD)
                .orElse(null);
        if (schedule == null || !schedule.isEnabled()) {
            cancelSchedule(NotificationScheduleType.LEADERBOARD);
            return;
        }

        cancelSchedule(NotificationScheduleType.LEADERBOARD);

        LocalTime sendTime = schedule.getSendTime() != null ? schedule.getSendTime() : LocalTime.of(16, 0);
        ZonedDateTime now = ZonedDateTime.now(VIETNAM_ZONE);
        ZonedDateTime firstRun = now.withHour(sendTime.getHour()).withMinute(sendTime.getMinute()).withSecond(0);
        if (firstRun.isBefore(now)) firstRun = firstRun.plusDays(1);

        long oneDay = Duration.ofDays(1).toMillis();
        ScheduledFuture<?> task = taskScheduler.scheduleAtFixedRate(() -> {
            DayOfWeek today = LocalDate.now(VIETNAM_ZONE).getDayOfWeek();
            if (today == DayOfWeek.FRIDAY) {
                runLegacySchedule(NotificationScheduleType.LEADERBOARD, "LEGACY_LEADERBOARD_NOTIFICATION",
                        chatopsNotificationService::sendLeaderboardNotification);
            } else {
                logLegacySchedule(NotificationScheduleType.LEADERBOARD, "LEGACY_LEADERBOARD_NOTIFICATION", SystemLogStatus.SKIPPED,
                        SystemLogSeverity.INFO, 0, "Not Friday", null);
            }
        }, Date.from(firstRun.toInstant()), oneDay);
        tasks.put(NotificationScheduleType.LEADERBOARD, task);
        log.info("Scheduled leaderboard notification at {} every Friday", sendTime);
    }

    private boolean isUnifiedNotificationJobEnabled(NotificationScheduleType type) {
        String jobCode = switch (type) {
            case BIRTHDAY, ANNIVERSARY -> NotificationJobService.CRON_BIRTHDAY_ANNIVERSARY;
            case EVENT -> NotificationJobService.CRON_EVENT_REMINDER;
            case LATE, LEADERBOARD -> null;
        };
        if (jobCode == null) {
            return false;
        }
        return notificationJobRepository.findByCode(jobCode)
                .map(job -> job.isEnabled() && job.getSchedule() != null && !job.getSchedule().isBlank())
                .orElse(false);
    }

    private void runLegacySchedule(NotificationScheduleType type, String action, Runnable runnable) {
        long startedAt = System.nanoTime();
        try {
            runnable.run();
            logLegacySchedule(type, action, SystemLogStatus.SUCCESS, SystemLogSeverity.INFO,
                    (System.nanoTime() - startedAt) / 1_000_000, null, null);
        } catch (Exception ex) {
            logLegacySchedule(type, action, SystemLogStatus.FAILED, SystemLogSeverity.ERROR,
                    (System.nanoTime() - startedAt) / 1_000_000, ex.getMessage(), ex);
        }
    }

    private void logLegacySchedule(
            NotificationScheduleType type,
            String action,
            SystemLogStatus status,
            SystemLogSeverity severity,
            long durationMs,
            String reason,
            Throwable failure
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scheduleType", type);
        details.put("trigger", "legacy-schedule");
        details.put("reason", reason);

        systemLogService.log(new SystemLogCreateRequest(
                SystemLogCategory.TASK,
                severity,
                status,
                action,
                "ScheduleManager",
                SystemLogContext.getActorUsername(),
                SystemLogContext.getCorrelationId(),
                "NotificationSchedule",
                type != null ? type.name() : null,
                durationMs,
                action + " " + status.name().toLowerCase(),
                details,
                failure != null ? failure.getClass().getName() : null,
                SystemLogSanitizer.stackTrace(failure)
        ));
    }
}
