package org.example.dumanagementbackend.dto.luckydraw;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record LuckyDrawSessionRequest(
        @NotNull(message = "eventId is required")
        @Positive(message = "eventId must be greater than 0")
        Long eventId,

        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name
) {
}
