package org.example.dumanagementbackend.dto.seminar;

import org.example.dumanagementbackend.entity.enums.SeminarStatus;
import java.time.LocalDateTime;

public record SeminarRequest(
        Long speakerId,
        String title,
        String description,
        LocalDateTime scheduledAt,
        SeminarStatus status
) {
}
