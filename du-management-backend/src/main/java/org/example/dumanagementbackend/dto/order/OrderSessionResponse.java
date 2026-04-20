package org.example.dumanagementbackend.dto.order;

import org.example.dumanagementbackend.entity.enums.OrderSessionStatus;
import java.time.LocalDateTime;

public record OrderSessionResponse(
        Long id,
        OrderSessionStatus status,
        LocalDateTime deadline
) {
}
