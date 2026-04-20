package org.example.dumanagementbackend.dto.order;

import java.math.BigDecimal;

public record MenuItemResponse(
        Long id,
        String name,
        BigDecimal price
) {
}
