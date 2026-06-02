package org.example.dumanagementbackend.dto.project;

import java.time.LocalDateTime;

public record ProjectAvailabilitySummaryResponse(
        long openProjectCount,
        int availableMemberCount,
        LocalDateTime generatedAt
) {
}
