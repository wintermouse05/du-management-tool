package org.example.dumanagementbackend.dto.survey;

import java.util.List;

public record SurveyAssignmentUpdateRequest(
        List<Long> userIds
) {
}
