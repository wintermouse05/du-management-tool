package org.example.dumanagementbackend.dto.auth;

public record RegisterRequest(
        String username,
        String email,
        String fullName,
        String password
) {
}
