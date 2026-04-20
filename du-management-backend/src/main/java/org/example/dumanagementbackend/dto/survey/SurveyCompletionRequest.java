package org.example.dumanagementbackend.dto.survey;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SurveyCompletionRequest(
        @NotNull(message = "userId is required")
        @Positive(message = "userId must be greater than 0")
        Long userId,

        boolean completed
) {
}
