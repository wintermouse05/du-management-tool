package org.example.dumanagementbackend.dto.project;

public record AvailableProjectMemberResponse(
        Long id,
        String username,
        String fullName,
        String email,
        String roleName
) {
}
