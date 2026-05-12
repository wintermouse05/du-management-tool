package org.example.dumanagementbackend.dto.group;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record GroupMemberRequest(
        @NotNull(message = "userId is required")
        @Positive(message = "userId must be positive")
        Long userId
) {
}
