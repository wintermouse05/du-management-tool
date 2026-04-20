package org.example.dumanagementbackend.dto.order;

import org.example.dumanagementbackend.entity.enums.OrderSessionStatus;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record OrderSessionRequest(
        OrderSessionStatus status,

        @NotNull(message = "deadline is required")
        LocalDateTime deadline
) {
}
