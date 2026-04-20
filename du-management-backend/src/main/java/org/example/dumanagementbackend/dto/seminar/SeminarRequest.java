package org.example.dumanagementbackend.dto.seminar;

import org.example.dumanagementbackend.entity.enums.SeminarStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record SeminarRequest(
        Long speakerId,

        @NotBlank(message = "title is required")
        @Size(max = 255, message = "title must be at most 255 characters")
        String title,

        @Size(max = 4000, message = "description must be at most 4000 characters")
        String description,

        LocalDateTime scheduledAt,

        SeminarStatus status
) {
}
