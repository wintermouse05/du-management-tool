package org.example.dumanagementbackend.dto.auth;

public record LoginRequest(
        String username,
        String password
) {
}
