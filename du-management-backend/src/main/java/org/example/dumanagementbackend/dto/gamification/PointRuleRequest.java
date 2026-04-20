package org.example.dumanagementbackend.dto.gamification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PointRuleRequest(
        @NotBlank(message = "actionCode is required")
        @Size(max = 100, message = "actionCode must be at most 100 characters")
        String actionCode,

        @NotNull(message = "pointValue is required")
        Integer pointValue
) {
}
