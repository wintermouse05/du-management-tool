package org.example.dumanagementbackend.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.example.dumanagementbackend.entity.enums.SystemLogCategory;
import org.example.dumanagementbackend.entity.enums.SystemLogStatus;
import org.example.dumanagementbackend.logging.QueuedBackendLogEvent;
import org.example.dumanagementbackend.logging.SystemLogbackAppender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SystemLogbackFlushService {

    private static final int MAX_BATCH_SIZE = 250;

    private final SystemLogService systemLogService;

    @Scheduled(fixedDelayString = "${app.system-logs.logback-flush-ms:5000}")
    public void flushQueuedLogEvents() {
        List<QueuedBackendLogEvent> events = new ArrayList<>();
        SystemLogbackAppender.drainTo(events, MAX_BATCH_SIZE);
        if (events.isEmpty()) {
            return;
        }

        List<SystemLogCreateRequest> requests = events.stream()
                .map(this::toRequest)
                .toList();
        systemLogService.logAll(requests);
    }

    private SystemLogCreateRequest toRequest(QueuedBackendLogEvent event) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("loggerName", event.loggerName());
        details.put("threadName", event.threadName());
        details.put("mdc", event.mdc());

        return new SystemLogCreateRequest(
                SystemLogCategory.BACKEND_LOG,
                event.severity(),
                event.exceptionClass() == null ? SystemLogStatus.SUCCESS : SystemLogStatus.FAILED,
                "LOG",
                event.loggerName(),
                null,
                event.correlationId(),
                "Logger",
                event.loggerName(),
                null,
                event.message(),
                details,
                event.exceptionClass(),
                event.stackTrace()
        );
    }
}
