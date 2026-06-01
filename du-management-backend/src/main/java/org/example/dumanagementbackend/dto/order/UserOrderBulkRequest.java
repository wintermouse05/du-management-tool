package org.example.dumanagementbackend.dto.order;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UserOrderBulkRequest(
        @NotNull(message = "sessionId is required")
        @Positive(message = "sessionId must be greater than 0")
        Long sessionId,

        @NotEmpty(message = "userIds is required")
        List<@NotNull(message = "userIds cannot contain null values") @Positive(message = "userIds must be greater than 0") Long> userIds,

        @NotNull(message = "itemId is required")
        @Positive(message = "itemId must be greater than 0")
        Long itemId,

        @NotNull(message = "quantity is required")
        @Positive(message = "quantity must be greater than 0")
        Integer quantity,

        @Size(max = 255, message = "note must be at most 255 characters")
        String note,

        Boolean paid
) {
}
