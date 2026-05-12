package org.example.dumanagementbackend.dto.group;

public record GroupMemberResponse(
        Long id,
        String username,
        String fullName,
        String email
) {
}
