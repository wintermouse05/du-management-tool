package org.example.dumanagementbackend.dto.project;

import java.time.LocalDateTime;

import org.example.dumanagementbackend.entity.enums.ProjectStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProjectRequest(
        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        @NotNull(message = "status is required")
        ProjectStatus status,

        @NotNull(message = "startTime is required")
        LocalDateTime startTime,

        @NotNull(message = "endTime is required")
        LocalDateTime endTime
) {
}
