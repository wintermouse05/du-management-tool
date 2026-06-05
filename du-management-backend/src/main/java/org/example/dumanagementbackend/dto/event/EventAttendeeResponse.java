package org.example.dumanagementbackend.dto.event;

import org.example.dumanagementbackend.entity.enums.RsvpStatus;
import org.example.dumanagementbackend.entity.enums.UserStatus;

public record EventAttendeeResponse(
        Long eventId,
        Long userId,
        String fullName,
        UserStatus userStatus,
        RsvpStatus rsvpStatus,
        boolean checkedIn
) {
}
