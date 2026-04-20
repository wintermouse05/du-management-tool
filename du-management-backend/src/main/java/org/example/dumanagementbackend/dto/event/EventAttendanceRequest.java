package org.example.dumanagementbackend.dto.event;

import org.example.dumanagementbackend.entity.enums.RsvpStatus;

public record EventAttendanceRequest(
        Long userId,
        RsvpStatus rsvpStatus
) {
}
