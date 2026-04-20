package org.example.dumanagementbackend.dto.order;

public record UserOrderResponse(
        Long id,
        Long sessionId,
        Long userId,
        String fullName,
        Long itemId,
        String itemName,
        Integer quantity,
        String note,
        boolean paid
) {
}
