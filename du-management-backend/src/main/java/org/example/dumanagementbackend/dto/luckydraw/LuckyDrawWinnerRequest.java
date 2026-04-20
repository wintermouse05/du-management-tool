package org.example.dumanagementbackend.dto.luckydraw;

public record LuckyDrawWinnerRequest(
        Long prizeId,
        Long userId
) {
}
