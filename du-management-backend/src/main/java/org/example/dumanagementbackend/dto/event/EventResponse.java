package org.example.dumanagementbackend.dto.event;

import java.time.LocalDateTime;

public record EventResponse(
        Long id,
        String name,
        LocalDateTime eventDate,
        String location
) {
}
