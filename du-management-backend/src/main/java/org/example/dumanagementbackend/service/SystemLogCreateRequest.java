package org.example.dumanagementbackend.service;

import org.example.dumanagementbackend.entity.enums.SystemLogCategory;
import org.example.dumanagementbackend.entity.enums.SystemLogSeverity;
import org.example.dumanagementbackend.entity.enums.SystemLogStatus;

public record SystemLogCreateRequest(
        SystemLogCategory category,
        SystemLogSeverity severity,
        SystemLogStatus status,
        String action,
        String source,
        String actorUsername,
        String correlationId,
        String targetType,
        String targetId,
        Long durationMs,
        String message,
        Object details,
        String exceptionClass,
        String stackTrace
) {
}
