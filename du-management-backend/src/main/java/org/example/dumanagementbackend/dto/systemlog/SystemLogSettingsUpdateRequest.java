package org.example.dumanagementbackend.dto.systemlog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SystemLogSettingsUpdateRequest(
        @NotNull(message = "retentionDays is required")
        @Min(value = 1, message = "retentionDays must be at least 1")
        @Max(value = 3650, message = "retentionDays must be at most 3650")
        Integer retentionDays
) {
}
