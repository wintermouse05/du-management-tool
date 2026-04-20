package org.example.dumanagementbackend.dto.order;

public record UserOrderRequest(
        Long sessionId,
        Long userId,
        Long itemId,
        Integer quantity,
        String note,
        Boolean paid
) {
}
