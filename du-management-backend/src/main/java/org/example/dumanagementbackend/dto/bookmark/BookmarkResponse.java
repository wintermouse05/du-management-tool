package org.example.dumanagementbackend.dto.bookmark;

import java.time.LocalDateTime;

public record BookmarkResponse(
        Long id,
        String title,
        String url,
        String description,
        String category,
        boolean pinned,
        String createdBy,
        LocalDateTime updatedAt,
        String updatedBy
) {
}
