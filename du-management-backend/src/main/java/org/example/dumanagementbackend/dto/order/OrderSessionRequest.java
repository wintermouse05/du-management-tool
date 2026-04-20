package org.example.dumanagementbackend.dto.order;

import org.example.dumanagementbackend.entity.enums.OrderSessionStatus;
import java.time.LocalDateTime;

public record OrderSessionRequest(
        OrderSessionStatus status,
        LocalDateTime deadline
) {
}
