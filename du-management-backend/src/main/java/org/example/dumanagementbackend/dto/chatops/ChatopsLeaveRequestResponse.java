package org.example.dumanagementbackend.dto.chatops;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ChatopsLeaveRequestResponse(
        String postId,
        String userId,
        String requesterName,
        ChatopsLeaveRequestType type,
        LocalDate requestedDate,
        LocalDateTime postedAt,
        String message,
        String matchedText
) {
}
