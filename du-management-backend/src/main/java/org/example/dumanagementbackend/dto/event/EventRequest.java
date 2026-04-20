package org.example.dumanagementbackend.dto.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record EventRequest(
        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        @NotNull(message = "eventDate is required")
        LocalDateTime eventDate,

        @Size(max = 255, message = "location must be at most 255 characters")
        String location
) {
}
