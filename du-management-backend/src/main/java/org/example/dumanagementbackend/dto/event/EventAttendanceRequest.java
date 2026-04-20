package org.example.dumanagementbackend.dto.event;

import org.example.dumanagementbackend.entity.enums.RsvpStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EventAttendanceRequest(
        @NotNull(message = "userId is required")
        @Positive(message = "userId must be greater than 0")
        Long userId,

        RsvpStatus rsvpStatus
) {
}
