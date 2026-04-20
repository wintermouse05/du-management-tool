package org.example.dumanagementbackend.dto.luckydraw;

public record LuckyDrawPrizeResponse(
        Long id,
        Long sessionId,
        String sessionName,
        String prizeName,
        Integer quantity
) {
}
