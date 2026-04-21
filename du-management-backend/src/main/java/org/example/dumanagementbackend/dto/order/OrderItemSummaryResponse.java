package org.example.dumanagementbackend.dto.order;

import java.math.BigDecimal;

public record OrderItemSummaryResponse(
        Long itemId,
        String itemName,
        BigDecimal unitPrice,
        Integer totalQuantity,
        BigDecimal totalAmount
) {
}
