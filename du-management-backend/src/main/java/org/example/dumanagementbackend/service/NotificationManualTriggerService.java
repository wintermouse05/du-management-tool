package org.example.dumanagementbackend.service;

import java.util.concurrent.CompletableFuture;

import org.example.dumanagementbackend.entity.enums.SystemLogCategory;
import org.example.dumanagementbackend.entity.enums.SystemLogSeverity;
import org.example.dumanagementbackend.entity.enums.SystemLogStatus;
import org.example.dumanagementbackend.logging.SystemLogContext;
import org.example.dumanagementbackend.logging.SystemLogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationManualTriggerService {

    private final NotificationService notificationService;
    private final SystemLogService systemLogService;

    @Async
    public CompletableFuture<Void> triggerSurveyReminderAsync(Long surveyId) {
        long startedAt = System.nanoTime();
        try {
            String result = notificationService.triggerSurveyReminder(surveyId);
            log.info("Manual survey reminder completed: {}", result);
            logManualTask(surveyId, result, SystemLogStatus.SUCCESS, SystemLogSeverity.INFO, startedAt, null);
        } catch (Exception ex) {
            log.error("Manual survey reminder failed for surveyId={}: {}", surveyId, ex.getMessage(), ex);
            logManualTask(surveyId, ex.getMessage(), SystemLogStatus.FAILED, SystemLogSeverity.ERROR, startedAt, ex);
        }
        return CompletableFuture.completedFuture(null);
    }

    private void logManualTask(
            Long surveyId,
            String message,
            SystemLogStatus status,
            SystemLogSeverity severity,
            long startedAt,
            Throwable failure
    ) {
        java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("surveyId", surveyId);
        details.put("trigger", "manual");
        details.put("result", message);

        systemLogService.log(new SystemLogCreateRequest(
                SystemLogCategory.TASK,
                severity,
                status,
                "MANUAL_SURVEY_REMINDER",
                "NotificationManualTriggerService",
                SystemLogContext.getActorUsername(),
                SystemLogContext.getCorrelationId(),
                "Survey",
                surveyId != null ? String.valueOf(surveyId) : null,
                (System.nanoTime() - startedAt) / 1_000_000,
                "Manual survey reminder " + status.name().toLowerCase(),
                details,
                failure != null ? failure.getClass().getName() : null,
                SystemLogSanitizer.stackTrace(failure)
        ));
    }
}
