package org.example.dumanagementbackend.dto.project;

import java.time.LocalDateTime;

import org.example.dumanagementbackend.entity.enums.ProjectStatus;

public record ProjectResponse(
        Long id,
        String name,
        ProjectStatus status,
        String statusLabel,
        LocalDateTime startTime,
        LocalDateTime endTime,
        int memberCount,
        int taskCount
) {
}
