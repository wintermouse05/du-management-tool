package org.example.dumanagementbackend.dto.luckydraw;

import java.util.List;

import jakarta.validation.constraints.NotNull;

public record LuckyDrawParticipantSetupRequest(
        @NotNull(message = "participantIds is required")
        List<Long> participantIds
) {
}
