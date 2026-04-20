package org.example.dumanagementbackend.dto.survey;

public record SurveyProgressResponse(
        Long surveyId,
        long totalAssigned,
        long completedCount
) {
}
