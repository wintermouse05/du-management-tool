package org.example.dumanagementbackend.dto.luckydraw;

public record LuckyDrawSessionResponse(
        Long id,
        Long eventId,
        String eventName,
        String name
) {
}
