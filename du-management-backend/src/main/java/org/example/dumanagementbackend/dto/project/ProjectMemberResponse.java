package org.example.dumanagementbackend.dto.project;

import java.time.LocalDateTime;

import org.example.dumanagementbackend.entity.enums.ProjectRole;

public record ProjectMemberResponse(
        Long projectId,
        Long userId,
        String username,
        String fullName,
        String email,
        ProjectRole projectRole,
        String projectRoleLabel,
        LocalDateTime participationStartTime,
        LocalDateTime expectedEndTime
) {
}
