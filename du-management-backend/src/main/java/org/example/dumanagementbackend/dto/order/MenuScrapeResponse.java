package org.example.dumanagementbackend.dto.order;

import java.util.List;

public record MenuScrapeResponse(
        String restaurantName,
        List<MenuScrapeItemResponse> items
) {
}
