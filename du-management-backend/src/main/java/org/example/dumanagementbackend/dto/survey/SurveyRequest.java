package org.example.dumanagementbackend.dto.survey;

import java.time.LocalDateTime;

public record SurveyRequest(
        String title,
        String link,
        LocalDateTime deadline
) {
}
