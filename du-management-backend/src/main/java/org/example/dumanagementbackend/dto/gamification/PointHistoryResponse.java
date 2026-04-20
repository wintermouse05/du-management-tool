package org.example.dumanagementbackend.dto.gamification;

import java.time.LocalDateTime;

public record PointHistoryResponse(
        Long id,
        Long userId,
        String fullName,
        Long ruleId,
        String actionCode,
        Integer pointsChanged,
        String reason,
        LocalDateTime createdAt
) {
}
