package org.example.dumanagementbackend.dto.notification;

public record NotificationJobResponse(
        String code,
        String schedule,
        String description
) {
}
