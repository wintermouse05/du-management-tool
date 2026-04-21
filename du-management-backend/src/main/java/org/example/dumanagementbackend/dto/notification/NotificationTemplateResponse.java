package org.example.dumanagementbackend.dto.notification;

import java.time.LocalDateTime;

public record NotificationTemplateResponse(
        Long id,
        String code,
        String name,
        String subjectTemplate,
        String bodyTemplate,
        boolean enabled,
        LocalDateTime updatedAt
) {
}