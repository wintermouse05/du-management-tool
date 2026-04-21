package org.example.dumanagementbackend.service;

import java.time.LocalDateTime;
import java.util.List;
import org.example.dumanagementbackend.dto.notification.NotificationJobResponse;
import org.example.dumanagementbackend.entity.NotificationJob;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.NotificationJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationJobService {

    public static final String CRON_BIRTHDAY_ANNIVERSARY = "CRON_BIRTHDAY_ANNIVERSARY";
    public static final String CRON_EVENT_REMINDER = "CRON_EVENT_REMINDER";
    public static final String CRON_SURVEY_REMINDER = "CRON_SURVEY_REMINDER";

    private final NotificationJobRepository notificationJobRepository;

    @Transactional
    public List<NotificationJobResponse> getJobs() {
        ensureDefaultJobs();
        return notificationJobRepository.findAllByOrderByCodeAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NotificationJobResponse setEnabled(String code, boolean enabled) {
        NotificationJob job = notificationJobRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Notification job not found with code=" + code));
        job.setEnabled(enabled);
        return toResponse(notificationJobRepository.save(job));
    }

    @Transactional
    public void markJobRun(String code, LocalDateTime runAt) {
        NotificationJob job = notificationJobRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Notification job not found with code=" + code));
        job.setLastRunAt(runAt);
        notificationJobRepository.save(job);
    }

    @Transactional
    public List<NotificationJob> getEnabledJobsForDispatch() {
        ensureDefaultJobs();
        return notificationJobRepository.findByEnabledTrueOrderByCodeAsc();
    }

    public boolean shouldRun(NotificationJob job, LocalDateTime now) {
        CronExpression cronExpression;
        try {
            cronExpression = CronExpression.parse(job.getSchedule());
        } catch (IllegalArgumentException ex) {
            return false;
        }

        LocalDateTime baseline = job.getLastRunAt() != null
                ? job.getLastRunAt().withSecond(0).withNano(0)
                : now.minusMinutes(2);

        LocalDateTime nextExecution = cronExpression.next(baseline);
        return nextExecution != null && !nextExecution.isAfter(now);
    }

    @Transactional
    public void ensureDefaultJobs() {
        ensureJob(
                CRON_BIRTHDAY_ANNIVERSARY,
                "0 0 8 * * *",
                "Daily birthday and join-date anniversary notification",
                true
        );
        ensureJob(
                CRON_EVENT_REMINDER,
                "0 0 * * * *",
                "Remind one hour before seminar/event",
                true
        );
        ensureJob(
                CRON_SURVEY_REMINDER,
                "0 0 9 * * *",
                "Remind users before survey deadline",
                true
        );
    }

    private void ensureJob(String code, String schedule, String description, boolean enabled) {
        if (notificationJobRepository.findByCode(code).isPresent()) {
            return;
        }
        NotificationJob job = new NotificationJob();
        job.setCode(code);
        job.setSchedule(schedule);
        job.setDescription(description);
        job.setEnabled(enabled);
        notificationJobRepository.save(job);
    }

    private NotificationJobResponse toResponse(NotificationJob job) {
        return new NotificationJobResponse(
                job.getCode(),
                job.getSchedule(),
                job.getDescription(),
                job.isEnabled(),
                job.getLastRunAt()
        );
    }
}