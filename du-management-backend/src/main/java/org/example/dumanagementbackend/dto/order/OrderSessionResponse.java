package org.example.dumanagementbackend.dto.order;

import org.example.dumanagementbackend.entity.enums.OrderSessionStatus;
import java.time.LocalDateTime;

public record OrderSessionResponse(
        Long id,
        String name,
        OrderSessionStatus status,
        LocalDateTime deadline,
        Long restaurantId,
        String restaurantName,
        String creatorName,
        String creatorUsername,
        boolean canManage,
        LocalDateTime createdAt
) {
}
