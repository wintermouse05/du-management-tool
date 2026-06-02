package org.example.dumanagementbackend.dto.project;

import java.time.LocalDateTime;

import org.example.dumanagementbackend.entity.enums.ProjectRole;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProjectMemberRequest(
        @NotNull(message = "userId is required")
        @Positive(message = "userId must be positive")
        Long userId,

        @NotNull(message = "projectRole is required")
        ProjectRole projectRole,

        @NotNull(message = "participationStartTime is required")
        LocalDateTime participationStartTime,

        @NotNull(message = "expectedEndTime is required")
        LocalDateTime expectedEndTime
) {
}
