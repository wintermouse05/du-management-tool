package org.example.dumanagementbackend.dto.luckydraw;

public record LuckyDrawPrizeRequest(
        Long sessionId,
        String prizeName,
        Integer quantity
) {
}
