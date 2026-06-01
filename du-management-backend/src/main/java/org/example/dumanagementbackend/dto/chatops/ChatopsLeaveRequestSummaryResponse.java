package org.example.dumanagementbackend.dto.chatops;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ChatopsLeaveRequestSummaryResponse(
        LocalDate date,
        LocalDateTime fetchedAt,
        boolean chatopsEnabled,
        String errorMessage,
        int total,
        int wfhCount,
        int offCount,
        List<ChatopsLeaveRequestResponse> requests
) {
}
