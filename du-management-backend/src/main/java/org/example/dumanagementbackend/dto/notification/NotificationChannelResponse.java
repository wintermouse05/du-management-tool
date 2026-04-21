package org.example.dumanagementbackend.dto.notification;

import org.example.dumanagementbackend.entity.enums.NotificationChannelType;

public record NotificationChannelResponse(
        Long id,
        NotificationChannelType type,
        String endpoint,
        boolean enabled
) {
}
