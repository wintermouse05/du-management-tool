package org.example.dumanagementbackend.dto.project;

import java.time.LocalDateTime;
import java.util.List;

import org.example.dumanagementbackend.entity.enums.TaskStatus;

public record ProjectTaskResponse(
        Long id,
        Long projectId,
        String projectName,
        String name,
        String description,
        TaskStatus status,
        String statusLabel,
        List<ProjectTaskAssigneeResponse> assignees,
        LocalDateTime startTime,
        LocalDateTime deadline
) {
}
