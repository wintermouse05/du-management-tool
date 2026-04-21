package org.example.dumanagementbackend.dto.notification;

import jakarta.validation.constraints.NotNull;

public record NotificationJobToggleRequest(
        @NotNull(message = "enabled is required")
        Boolean enabled
) {
}