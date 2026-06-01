package org.example.dumanagementbackend.dto.survey;

public record SurveyAssignmentStatusResponse(
        Long userId,
        String fullName,
        boolean completed
) {
}
