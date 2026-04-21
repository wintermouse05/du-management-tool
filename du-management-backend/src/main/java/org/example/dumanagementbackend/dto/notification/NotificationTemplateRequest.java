package org.example.dumanagementbackend.dto.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NotificationTemplateRequest(
        @NotBlank(message = "code is required")
        @Size(max = 80, message = "code must be at most 80 characters")
        String code,

        @NotBlank(message = "name is required")
        @Size(max = 120, message = "name must be at most 120 characters")
        String name,

        @NotBlank(message = "subjectTemplate is required")
        @Size(max = 255, message = "subjectTemplate must be at most 255 characters")
        String subjectTemplate,

        @NotBlank(message = "bodyTemplate is required")
        @Size(max = 2000, message = "bodyTemplate must be at most 2000 characters")
        String bodyTemplate,

        @NotNull(message = "enabled is required")
        Boolean enabled
) {
}