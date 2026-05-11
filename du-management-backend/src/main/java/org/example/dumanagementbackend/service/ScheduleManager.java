package org.example.dumanagementbackend.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.example.dumanagementbackend.entity.NotificationSchedule;
import org.example.dumanagementbackend.entity.enums.NotificationScheduleType;
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
    private final ChatopsNotificationService chatopsNotificationService;

    private final Map<NotificationScheduleType, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    public synchronized void updateSchedule(NotificationScheduleType type) {
        switch (type) {
            case BIRTHDAY -> rescheduleBirthdayTask();
            case ANNIVERSARY -> rescheduleAnniversaryTask();
            case LATE -> scheduleLateNotificationTask();
            case EVENT -> rescheduleEventTask();
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
        ScheduledFuture<?> task = taskScheduler.scheduleAtFixedRate(
                chatopsNotificationService::sendBirthdayNotification,
                Date.from(firstRun.toInstant()),
                oneDay
        );
        tasks.put(NotificationScheduleType.BIRTHDAY, task);
        log.info("Scheduled birthday notification at {} daily", sendTime);
    }

    // ---- anniversary ----

    public synchronized void rescheduleAnniversaryTask() {
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
        ScheduledFuture<?> task = taskScheduler.scheduleAtFixedRate(
                chatopsNotificationService::sendAnniversaryNotification,
                Date.from(firstRun.toInstant()),
                oneDay
        );
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
                chatopsNotificationService.sendLatePenaltyNotification();
            }
        }, Date.from(firstRun.toInstant()), oneDay);
        tasks.put(NotificationScheduleType.LATE, task);
        log.info("Scheduled late penalty notification at {} Mon-Fri", sendTime);
    }

    // ---- event (Mon-Fri) ----

    public synchronized void rescheduleEventTask() {
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
            DayOfWeek today = LocalDate.now(VIETNAM_ZONE).getDayOfWeek();
            if (today != DayOfWeek.SATURDAY && today != DayOfWeek.SUNDAY) {
                chatopsNotificationService.sendEventNotification();
            }
        }, Date.from(firstRun.toInstant()), oneDay);
        tasks.put(NotificationScheduleType.EVENT, task);
        log.info("Scheduled event notification at {} Mon-Fri", sendTime);
    }
}
