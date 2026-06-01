package org.example.dumanagementbackend.dto.bookmark;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BookmarkRequest(
        @NotBlank(message = "title is required")
        @Size(max = 255, message = "title must be at most 255 characters")
        String title,

        @NotBlank(message = "url is required")
        @Size(max = 500, message = "url must be at most 500 characters")
        @Pattern(regexp = "^(https?://).+", message = "url must start with http:// or https://")
        String url,

        @Size(max = 1000, message = "description must be at most 1000 characters")
        String description,

        @Size(max = 120, message = "category must be at most 120 characters")
        String category,

        boolean pinned
) {
}
