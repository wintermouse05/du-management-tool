package org.example.dumanagementbackend.dto.order;

import java.math.BigDecimal;
import org.example.dumanagementbackend.entity.enums.OrderSessionStatus;

public record UserOrderResponse(
        Long id,
        Long sessionId,
        String sessionName,
        OrderSessionStatus sessionStatus,
        Long userId,
        String fullName,
        String orderedByFullName,
        Long itemId,
        String itemName,
        BigDecimal itemPrice,
        Integer quantity,
        String note,
        boolean paid,
        boolean canManage
) {
}
