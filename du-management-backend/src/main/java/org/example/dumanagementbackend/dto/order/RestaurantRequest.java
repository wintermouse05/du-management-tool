package org.example.dumanagementbackend.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RestaurantRequest(
        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        @NotBlank(message = "scrapeUrl is required")
        @Size(max = 1024, message = "scrapeUrl must be at most 1024 characters")
        String scrapeUrl
) {
}
