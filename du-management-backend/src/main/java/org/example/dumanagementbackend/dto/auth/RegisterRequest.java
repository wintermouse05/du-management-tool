package org.example.dumanagementbackend.dto.auth;

import org.example.dumanagementbackend.validation.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "username is required")
        @Size(min = 3, max = 100, message = "username must be between 3 and 100 characters")
        String username,

        @NotBlank(message = "email is required")
        @Email(message = "email format is invalid")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        @NotBlank(message = "fullName is required")
        @Size(max = 255, message = "fullName must be at most 255 characters")
        String fullName,

        @NotBlank(message = "password is required")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.FORMAT_MESSAGE)
        String password,

        java.time.LocalDate dob
) {
}
