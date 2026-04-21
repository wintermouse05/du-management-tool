package org.example.dumanagementbackend.dto.notification;

import java.time.LocalDateTime;

public record NotificationInboxResponse(
        Long id,
        String title,
        String message,
        String type,
        boolean read,
        String actionUrl,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {
}