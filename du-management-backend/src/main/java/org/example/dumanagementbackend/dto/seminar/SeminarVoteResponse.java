package org.example.dumanagementbackend.dto.seminar;

import org.example.dumanagementbackend.entity.enums.VoteType;

public record SeminarVoteResponse(
        Long seminarId,
        Long userId,
        String fullName,
        VoteType voteType
) {
}
