package org.example.dumanagementbackend.dto.project;

import java.time.LocalDateTime;

import org.example.dumanagementbackend.entity.enums.ProjectRole;
import org.example.dumanagementbackend.entity.enums.UserStatus;

public record ProjectMemberResponse(
        Long projectId,
        Long userId,
        String username,
        String fullName,
        String email,
        UserStatus status,
        ProjectRole projectRole,
        String projectRoleLabel,
        LocalDateTime participationStartTime,
        LocalDateTime expectedEndTime
) {
}
