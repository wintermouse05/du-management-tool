package org.example.dumanagementbackend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.example.dumanagementbackend.entity.enums.SystemLogCategory;
import org.example.dumanagementbackend.entity.enums.SystemLogSeverity;
import org.example.dumanagementbackend.entity.enums.SystemLogStatus;

public record SystemLogSearchCriteria(
        String q,
        List<SystemLogCategory> categories,
        SystemLogSeverity severity,
        SystemLogStatus status,
        String source,
        String actor,
        String correlationId,
        LocalDateTime from,
        LocalDateTime to
) {
}
