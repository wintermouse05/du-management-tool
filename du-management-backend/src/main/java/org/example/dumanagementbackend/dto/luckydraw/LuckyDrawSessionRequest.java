package org.example.dumanagementbackend.dto.luckydraw;

public record LuckyDrawSessionRequest(
        Long eventId,
        String name
) {
}
