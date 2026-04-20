package org.example.dumanagementbackend.dto.luckydraw;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record LuckyDrawWinnerRequest(
        @NotNull(message = "prizeId is required")
        @Positive(message = "prizeId must be greater than 0")
        Long prizeId,

        @NotNull(message = "userId is required")
        @Positive(message = "userId must be greater than 0")
        Long userId
) {
}
