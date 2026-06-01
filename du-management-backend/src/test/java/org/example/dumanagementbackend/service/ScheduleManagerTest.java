package org.example.dumanagementbackend.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import org.example.dumanagementbackend.entity.NotificationJob;
import org.example.dumanagementbackend.entity.NotificationSchedule;
import org.example.dumanagementbackend.entity.enums.NotificationScheduleType;
import org.example.dumanagementbackend.repository.NotificationJobRepository;
import org.example.dumanagementbackend.repository.NotificationScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

@ExtendWith(MockitoExtension.class)
class ScheduleManagerTest {

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private NotificationScheduleRepository scheduleRepository;

    @Mock
    private NotificationJobRepository notificationJobRepository;

    @Mock
    private ChatopsNotificationService chatopsNotificationService;

    @Mock
    private SystemLogService systemLogService;

    @Mock
    private ScheduledFuture<Object> scheduledFuture;

    private ScheduleManager scheduleManager;

    @BeforeEach
    void setUp() {
        scheduleManager = new ScheduleManager(
                taskScheduler,
                scheduleRepository,
                notificationJobRepository,
                chatopsNotificationService,
                systemLogService
        );
    }

    @Test
    void rescheduleBirthdayTask_skipsLegacyScheduleWhenUnifiedJobIsEnabled() {
        NotificationJob job = new NotificationJob();
        job.setCode(NotificationJobService.CRON_BIRTHDAY_ANNIVERSARY);
        job.setEnabled(true);
        job.setSchedule("0 0 8 * * *");
        when(notificationJobRepository.findByCode(NotificationJobService.CRON_BIRTHDAY_ANNIVERSARY))
                .thenReturn(Optional.of(job));

        scheduleManager.rescheduleBirthdayTask();

        verify(scheduleRepository, never()).findByType(NotificationScheduleType.BIRTHDAY);
        verify(taskScheduler, never()).scheduleAtFixedRate(any(Runnable.class), any(Date.class), anyLong());
    }

    @Test
    void rescheduleBirthdayTask_schedulesLegacyTaskWhenUnifiedJobIsDisabled() {
        NotificationJob job = new NotificationJob();
        job.setCode(NotificationJobService.CRON_BIRTHDAY_ANNIVERSARY);
        job.setEnabled(false);
        job.setSchedule("0 0 8 * * *");
        when(notificationJobRepository.findByCode(NotificationJobService.CRON_BIRTHDAY_ANNIVERSARY))
                .thenReturn(Optional.of(job));

        NotificationSchedule schedule = new NotificationSchedule();
        schedule.setType(NotificationScheduleType.BIRTHDAY);
        schedule.setEnabled(true);
        schedule.setSendTime(LocalTime.of(9, 5));
        when(scheduleRepository.findByType(NotificationScheduleType.BIRTHDAY))
                .thenReturn(Optional.of(schedule));
        doReturn(scheduledFuture).when(taskScheduler)
                .scheduleAtFixedRate(any(Runnable.class), any(Date.class), anyLong());
        scheduleManager.rescheduleBirthdayTask();

        verify(taskScheduler, times(1)).scheduleAtFixedRate(any(Runnable.class), any(Date.class), anyLong());
    }

    @Test
    void scheduleLeaderboardTask_schedulesTaskWhenEnabled() {
        NotificationSchedule schedule = new NotificationSchedule();
        schedule.setType(NotificationScheduleType.LEADERBOARD);
        schedule.setEnabled(true);
        schedule.setSendTime(LocalTime.of(16, 0));
        when(scheduleRepository.findByType(NotificationScheduleType.LEADERBOARD))
                .thenReturn(Optional.of(schedule));
        doReturn(scheduledFuture).when(taskScheduler)
                .scheduleAtFixedRate(any(Runnable.class), any(Date.class), anyLong());

        scheduleManager.scheduleLeaderboardTask();

        verify(taskScheduler, times(1)).scheduleAtFixedRate(any(Runnable.class), any(Date.class), anyLong());
    }

    @Test
    void scheduleLeaderboardTask_skipsWhenDisabled() {
        NotificationSchedule schedule = new NotificationSchedule();
        schedule.setType(NotificationScheduleType.LEADERBOARD);
        schedule.setEnabled(false);
        when(scheduleRepository.findByType(NotificationScheduleType.LEADERBOARD))
                .thenReturn(Optional.of(schedule));

        scheduleManager.scheduleLeaderboardTask();

        verify(taskScheduler, never()).scheduleAtFixedRate(any(Runnable.class), any(Date.class), anyLong());
    }
}
