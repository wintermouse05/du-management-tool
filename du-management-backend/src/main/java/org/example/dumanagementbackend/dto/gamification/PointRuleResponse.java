package org.example.dumanagementbackend.dto.gamification;

public record PointRuleResponse(
        Long id,
        String actionCode,
        Integer pointValue
) {
}
