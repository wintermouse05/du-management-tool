package org.example.dumanagementbackend.dto.order;

import jakarta.validation.constraints.NotBlank;

public record MenuScrapeRequest(
        @NotBlank(message = "url is required")
        String url
) {}
