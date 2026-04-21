package org.example.dumanagementbackend.dto.luckydraw;

public record LuckyDrawParticipantResponse(
        Long userId,
        String fullName,
        String email
) {
}
