package org.example.dumanagementbackend.dto.notification;

import org.example.dumanagementbackend.entity.enums.NotificationChannelType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NotificationChannelRequest(
        @NotNull(message = "type is required")
        NotificationChannelType type,

        @NotBlank(message = "endpoint is required")
        @Size(max = 500, message = "endpoint must be at most 500 characters")
        String endpoint,

        @NotNull(message = "enabled is required")
        Boolean enabled
) {
}
