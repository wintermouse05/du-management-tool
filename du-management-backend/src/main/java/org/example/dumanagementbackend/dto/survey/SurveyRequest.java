package org.example.dumanagementbackend.dto.survey;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record SurveyRequest(
        @NotBlank(message = "title is required")
        @Size(max = 255, message = "title must be at most 255 characters")
        String title,

        @NotBlank(message = "link is required")
        @Size(max = 500, message = "link must be at most 500 characters")
        @Pattern(regexp = "^(https?://).+", message = "link must start with http:// or https://")
        String link,

        @NotNull(message = "deadline is required")
        LocalDateTime deadline
) {
}
