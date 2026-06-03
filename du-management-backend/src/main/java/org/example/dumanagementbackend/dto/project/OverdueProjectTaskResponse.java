package org.example.dumanagementbackend.dto.project;

import java.time.LocalDateTime;

public record OverdueProjectTaskResponse(
        String taskName,
        String projectName,
        LocalDateTime deadline
) {
}
