package org.example.dumanagementbackend.dto.survey;

import java.time.LocalDateTime;

public record SurveyResponse(
        Long id,
        String title,
        String link,
        LocalDateTime deadline
) {
}
