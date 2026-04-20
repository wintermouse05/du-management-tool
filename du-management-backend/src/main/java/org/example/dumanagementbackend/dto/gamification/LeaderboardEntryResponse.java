package org.example.dumanagementbackend.dto.gamification;

public record LeaderboardEntryResponse(
        Long userId,
        String fullName,
        Integer totalPoints
) {
}
