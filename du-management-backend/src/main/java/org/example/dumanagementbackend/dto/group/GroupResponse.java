package org.example.dumanagementbackend.dto.group;

public record GroupResponse(
        Long id,
        String name,
        String description,
        boolean allGroup,
        int memberCount
) {
}
