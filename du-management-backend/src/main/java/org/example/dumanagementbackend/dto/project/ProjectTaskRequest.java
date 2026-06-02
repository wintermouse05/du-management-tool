package org.example.dumanagementbackend.dto.project;

import java.time.LocalDateTime;

import org.example.dumanagementbackend.entity.enums.TaskStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ProjectTaskRequest(
        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        @NotNull(message = "status is required")
        TaskStatus status,

        @NotNull(message = "assigneeId is required")
        @Positive(message = "assigneeId must be positive")
        Long assigneeId,

        @NotNull(message = "startTime is required")
        LocalDateTime startTime,

        @NotNull(message = "deadline is required")
        LocalDateTime deadline
) {
}
