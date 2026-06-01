package org.example.dumanagementbackend.dto.systemlog;

import java.time.LocalDateTime;

import org.example.dumanagementbackend.entity.enums.SystemLogCategory;
import org.example.dumanagementbackend.entity.enums.SystemLogSeverity;
import org.example.dumanagementbackend.entity.enums.SystemLogStatus;

public record SystemLogDetailResponse(
        Long id,
        LocalDateTime occurredAt,
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
        String detailsJson,
        String exceptionClass,
        String stackTrace
) {
}
