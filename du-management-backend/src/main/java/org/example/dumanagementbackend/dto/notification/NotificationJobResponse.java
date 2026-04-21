package org.example.dumanagementbackend.dto.notification;

import java.time.LocalDateTime;

public record NotificationJobResponse(
        String code,
        String schedule,
        String description,
        boolean enabled,
        LocalDateTime lastRunAt
) {
}
