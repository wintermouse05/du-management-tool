package org.example.dumanagementbackend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "token is required")
        String token,

        @NotBlank(message = "newPassword is required")
        @Size(min = 8, max = 128, message = "password must be between 8 and 128 characters")
        String newPassword
) {
}
