package org.example.dumanagementbackend.dto.seminar;

import org.example.dumanagementbackend.entity.enums.SeminarStatus;
import java.time.LocalDateTime;

public record SeminarResponse(
        Long id,
        Long speakerId,
        String speakerName,
        String title,
        String description,
        LocalDateTime scheduledAt,
        SeminarStatus status
) {
}
