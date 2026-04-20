package org.example.dumanagementbackend.dto.survey;

public record SurveyCompletionRequest(
        Long userId,
        boolean completed
) {
}
