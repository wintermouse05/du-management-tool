package org.example.dumanagementbackend.dto.auth;

public record LoginResponse(
        String accessToken,
        String tokenType,
        String username,
        String role,
        Long userId
) {
}
