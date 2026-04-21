package org.example.dumanagementbackend.dto.notification;

import java.time.LocalDateTime;

public record NotificationRealtimeResponse(
        Long id,
        String title,
        String message,
        String type,
        String actionUrl,
        LocalDateTime createdAt
) {
}