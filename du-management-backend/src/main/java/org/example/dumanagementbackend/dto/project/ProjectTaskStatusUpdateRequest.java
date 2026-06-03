package org.example.dumanagementbackend.dto.project;

import org.example.dumanagementbackend.entity.enums.TaskStatus;

import jakarta.validation.constraints.NotNull;

public record ProjectTaskStatusUpdateRequest(
        @NotNull(message = "status is required")
        TaskStatus status
) {
}
