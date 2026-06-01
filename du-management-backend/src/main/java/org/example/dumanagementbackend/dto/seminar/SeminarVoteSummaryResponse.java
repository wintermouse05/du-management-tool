package org.example.dumanagementbackend.dto.seminar;

public record SeminarVoteSummaryResponse(
        long upvotes,
        long downvotes
) {
}
