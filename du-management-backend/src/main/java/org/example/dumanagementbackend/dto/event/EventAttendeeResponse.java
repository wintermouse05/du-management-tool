package org.example.dumanagementbackend.dto.event;

import org.example.dumanagementbackend.entity.enums.RsvpStatus;

public record EventAttendeeResponse(
        Long eventId,
        Long userId,
        String fullName,
        RsvpStatus rsvpStatus,
        boolean checkedIn
) {
}
