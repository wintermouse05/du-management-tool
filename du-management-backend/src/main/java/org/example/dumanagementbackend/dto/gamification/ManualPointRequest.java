package org.example.dumanagementbackend.dto.gamification;

public record ManualPointRequest(
        Long userId,
        Long ruleId,
        Integer pointsChanged,
        String reason
) {
}
