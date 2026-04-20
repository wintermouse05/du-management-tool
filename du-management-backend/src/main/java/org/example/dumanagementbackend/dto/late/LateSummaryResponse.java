package org.example.dumanagementbackend.dto.late;

public record LateSummaryResponse(
        Long userId,
        String fullName,
        long totalLateTimes,
        long totalLateMinutes
) {
}
