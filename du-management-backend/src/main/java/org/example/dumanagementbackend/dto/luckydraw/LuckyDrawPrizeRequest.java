package org.example.dumanagementbackend.dto.luckydraw;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record LuckyDrawPrizeRequest(
        @NotNull(message = "sessionId is required")
        @Positive(message = "sessionId must be greater than 0")
        Long sessionId,

        @NotBlank(message = "prizeName is required")
        @Size(max = 255, message = "prizeName must be at most 255 characters")
        String prizeName,

        @NotNull(message = "quantity is required")
        @Positive(message = "quantity must be greater than 0")
        Integer quantity
) {
}
