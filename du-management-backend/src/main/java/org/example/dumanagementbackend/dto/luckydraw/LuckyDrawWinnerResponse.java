package org.example.dumanagementbackend.dto.luckydraw;

public record LuckyDrawWinnerResponse(
        Long id,
        Long prizeId,
        String prizeName,
        Long userId,
        String fullName
) {
}
