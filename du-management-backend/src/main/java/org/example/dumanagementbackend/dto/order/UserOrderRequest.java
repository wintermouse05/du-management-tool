package org.example.dumanagementbackend.dto.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UserOrderRequest(
        @NotNull(message = "sessionId is required")
        @Positive(message = "sessionId must be greater than 0")
        Long sessionId,

        @NotNull(message = "userId is required")
        @Positive(message = "userId must be greater than 0")
        Long userId,

        @NotNull(message = "itemId is required")
        @Positive(message = "itemId must be greater than 0")
        Long itemId,

        @NotNull(message = "quantity is required")
        @Positive(message = "quantity must be greater than 0")
        Integer quantity,

        @Size(max = 255, message = "note must be at most 255 characters")
        String note,

        Boolean paid
) {
}
