package org.example.dumanagementbackend.dto.gamification;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ManualPointRequest(
        @NotNull(message = "userId is required")
        @Positive(message = "userId must be greater than 0")
        Long userId,

        @Positive(message = "ruleId must be greater than 0")
        Long ruleId,

        Integer pointsChanged,

        @Size(max = 255, message = "reason must be at most 255 characters")
        String reason
) {
}
