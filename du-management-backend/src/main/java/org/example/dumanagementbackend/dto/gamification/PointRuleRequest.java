package org.example.dumanagementbackend.dto.gamification;

public record PointRuleRequest(
        String actionCode,
        Integer pointValue
) {
}
