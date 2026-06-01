package org.example.dumanagementbackend.dto.systemlog;

public record SystemLogSettingsResponse(
        int retentionDays,
        int defaultRetentionDays,
        int minRetentionDays,
        int maxRetentionDays
) {
}
