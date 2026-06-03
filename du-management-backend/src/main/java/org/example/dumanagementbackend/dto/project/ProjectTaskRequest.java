package org.example.dumanagementbackend.dto.project;

import java.time.LocalDateTime;
import java.util.List;

import org.example.dumanagementbackend.entity.enums.TaskStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ProjectTaskRequest(
        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        @Size(max = 2000, message = "description must be at most 2000 characters")
        String description,

        @NotNull(message = "status is required")
        TaskStatus status,

        @NotEmpty(message = "assigneeIds is required")
        List<@NotNull(message = "assigneeId is required") @Positive(message = "assigneeId must be positive") Long> assigneeIds,

        @NotNull(message = "startTime is required")
        LocalDateTime startTime,

        @NotNull(message = "deadline is required")
        LocalDateTime deadline
) {
}
