package org.example.dumanagementbackend.dto.event;

import java.time.LocalDateTime;

public record EventRequest(
        String name,
        LocalDateTime eventDate,
        String location
) {
}
