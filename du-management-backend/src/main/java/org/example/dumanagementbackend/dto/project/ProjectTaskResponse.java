package org.example.dumanagementbackend.dto.project;

import java.time.LocalDateTime;

import org.example.dumanagementbackend.entity.enums.TaskStatus;

public record ProjectTaskResponse(
        Long id,
        Long projectId,
        String projectName,
        String name,
        TaskStatus status,
        String statusLabel,
        Long assigneeId,
        String assigneeUsername,
        String assigneeFullName,
        LocalDateTime startTime,
        LocalDateTime deadline
) {
}
