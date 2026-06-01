package org.example.dumanagementbackend.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.example.dumanagementbackend.entity.NotificationJob;
import org.example.dumanagementbackend.entity.enums.SystemLogCategory;
import org.example.dumanagementbackend.entity.enums.SystemLogStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTest {

    @Mock
    private NotificationJobService notificationJobService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private SystemLogService systemLogService;

    private NotificationScheduler notificationScheduler;

    @BeforeEach
    void setUp() {
        notificationScheduler = new NotificationScheduler(
                notificationJobService,
                notificationService,
                systemLogService
        );
    }

    @Test
    void dispatchDueJobs_doesNotPersistTaskLogWhenNoNotificationsWereSent() {
        NotificationJob job = job(NotificationJobService.CRON_ORDER_SESSION_CREATOR_REMINDER);
        when(notificationJobService.getEnabledJobsForDispatch()).thenReturn(List.of(job));
        when(notificationJobService.shouldRun(eq(job), any(LocalDateTime.class))).thenReturn(true);
        when(notificationService.runOrderSessionCreatorReminderJob()).thenReturn(0);

        notificationScheduler.dispatchDueJobs();

        verify(systemLogService, never()).log(any(SystemLogCreateRequest.class));
        verify(notificationJobService).markJobRun(eq(job.getCode()), any(LocalDateTime.class));
    }

    @Test
    void dispatchDueJobs_persistsTaskLogWhenNotificationsWereSent() {
        NotificationJob job = job(NotificationJobService.CRON_ORDER_SESSION_CREATOR_REMINDER);
        when(notificationJobService.getEnabledJobsForDispatch()).thenReturn(List.of(job));
        when(notificationJobService.shouldRun(eq(job), any(LocalDateTime.class))).thenReturn(true);
        when(notificationService.runOrderSessionCreatorReminderJob()).thenReturn(2);

        notificationScheduler.dispatchDueJobs();

        verify(systemLogService).log(argThat(request ->
                request.category() == SystemLogCategory.TASK
                        && request.status() == SystemLogStatus.SUCCESS
                        && "NOTIFICATION_JOB".equals(request.action())
                        && job.getCode().equals(request.targetId())
        ));
        verify(notificationJobService).markJobRun(eq(job.getCode()), any(LocalDateTime.class));
    }

    @Test
    void dispatchDueJobs_persistsTaskLogWhenJobFails() {
        NotificationJob job = job(NotificationJobService.CRON_ORDER_SESSION_CREATOR_REMINDER);
        when(notificationJobService.getEnabledJobsForDispatch()).thenReturn(List.of(job));
        when(notificationJobService.shouldRun(eq(job), any(LocalDateTime.class))).thenReturn(true);
        when(notificationService.runOrderSessionCreatorReminderJob()).thenThrow(new IllegalStateException("boom"));

        notificationScheduler.dispatchDueJobs();

        verify(systemLogService).log(argThat(request ->
                request.category() == SystemLogCategory.TASK
                        && request.status() == SystemLogStatus.FAILED
                        && "NOTIFICATION_JOB".equals(request.action())
                        && job.getCode().equals(request.targetId())
        ));
        verify(notificationJobService).markJobRun(eq(job.getCode()), any(LocalDateTime.class));
    }

    private NotificationJob job(String code) {
        NotificationJob job = new NotificationJob();
        job.setCode(code);
        job.setSchedule("0 * * * * *");
        job.setDescription("Test job");
        job.setEnabled(true);
        return job;
    }
}
