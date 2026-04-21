package org.example.dumanagementbackend.dto.order;

import java.math.BigDecimal;
import java.util.List;

public record OrderSessionSummaryResponse(
        Long sessionId,
        Integer totalOrderLines,
        Integer totalQuantity,
        BigDecimal grandTotal,
        List<OrderItemSummaryResponse> items
) {
}
