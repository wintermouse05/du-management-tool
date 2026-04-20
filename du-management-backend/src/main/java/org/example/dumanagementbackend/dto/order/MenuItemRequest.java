package org.example.dumanagementbackend.dto.order;

import java.math.BigDecimal;

public record MenuItemRequest(
        String name,
        BigDecimal price
) {
}
