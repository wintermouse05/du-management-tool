package org.example.dumanagementbackend.dto.seminar;

import org.example.dumanagementbackend.entity.enums.VoteType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SeminarVoteRequest(
        @NotNull(message = "userId is required")
        @Positive(message = "userId must be greater than 0")
        Long userId,

        @NotNull(message = "voteType is required")
        VoteType voteType
) {
}
