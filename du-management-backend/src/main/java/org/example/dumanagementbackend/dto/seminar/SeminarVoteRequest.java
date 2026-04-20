package org.example.dumanagementbackend.dto.seminar;

import org.example.dumanagementbackend.entity.enums.VoteType;

public record SeminarVoteRequest(
        Long userId,
        VoteType voteType
) {
}
