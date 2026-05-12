package org.example.dumanagementbackend.dto.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GroupRequest(
        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        @Size(max = 1000, message = "description must be at most 1000 characters")
        String description,

        boolean allGroup
) {
}
