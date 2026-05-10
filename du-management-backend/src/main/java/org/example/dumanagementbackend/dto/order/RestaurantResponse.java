package org.example.dumanagementbackend.dto.order;

public record RestaurantResponse(
        Long id,
        String name,
        String scrapeUrl
) {
}
