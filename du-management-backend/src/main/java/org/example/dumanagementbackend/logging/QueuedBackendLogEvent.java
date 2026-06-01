package org.example.dumanagementbackend.logging;

import java.time.LocalDateTime;
import java.util.Map;

import org.example.dumanagementbackend.entity.enums.SystemLogSeverity;

public record QueuedBackendLogEvent(
        LocalDateTime occurredAt,
        SystemLogSeverity severity,
        String loggerName,
        String threadName,
        String message,
        String correlationId,
        String exceptionClass,
        String stackTrace,
        Map<String, String> mdc
) {
}
