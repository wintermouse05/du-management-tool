package org.example.dumanagementbackend.dto.survey;

import java.util.List;

public record SurveyProgressResponse(
        Long surveyId,
        long totalAssigned,
        long completedCount,
        List<SurveyAssignmentStatusResponse> assignments
) {
}
