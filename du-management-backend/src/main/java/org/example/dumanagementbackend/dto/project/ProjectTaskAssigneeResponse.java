package org.example.dumanagementbackend.dto.project;

public record ProjectTaskAssigneeResponse(
        Long id,
        String username,
        String fullName
) {
}
