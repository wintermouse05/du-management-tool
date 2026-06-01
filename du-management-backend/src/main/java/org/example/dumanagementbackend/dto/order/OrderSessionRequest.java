package org.example.dumanagementbackend.dto.order;

import org.example.dumanagementbackend.entity.enums.OrderSessionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

public record OrderSessionRequest(
        OrderSessionStatus status,

        @NotBlank(message = "name is required")
        String name,

        @NotNull(message = "restaurantId is required")
        @Positive(message = "restaurantId must be greater than 0")
        Long restaurantId,

        @NotNull(message = "deadline is required")
        LocalDateTime deadline
) {
}
