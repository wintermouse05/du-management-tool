package org.example.dumanagementbackend.service;

import java.util.List;

import org.example.dumanagementbackend.entity.NotificationSchedule;
import org.example.dumanagementbackend.repository.NotificationScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "chatops.enabled", havingValue = "true")
public class ScheduleStartupRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduleStartupRunner.class);

    private final NotificationScheduleRepository scheduleRepository;
    private final NotificationScheduleService notificationScheduleService;
    private final ScheduleManager scheduleManager;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        notificationScheduleService.ensureDefaultSchedules();
        List<NotificationSchedule> schedules = scheduleRepository.findByEnabledTrue();
        for (NotificationSchedule schedule : schedules) {
            try {
                scheduleManager.updateSchedule(schedule.getType());
                log.info("Registered schedule: type={} sendTime={}", schedule.getType(), schedule.getSendTime());
            } catch (Exception e) {
                log.error("Failed to register schedule for type={}: {}", schedule.getType(), e.getMessage());
            }
        }
    }
}
