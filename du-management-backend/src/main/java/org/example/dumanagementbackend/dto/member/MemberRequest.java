package org.example.dumanagementbackend.dto.member;

import org.example.dumanagementbackend.entity.enums.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record MemberRequest(
        @NotNull(message = "roleId is required")
        @Positive(message = "roleId must be greater than 0")
        Long roleId,

        @NotBlank(message = "username is required")
        @Size(min = 3, max = 100, message = "username must be between 3 and 100 characters")
        String username,

        @NotBlank(message = "email is required")
        @Email(message = "email format is invalid")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        @Size(min = 8, max = 128, message = "password must be between 8 and 128 characters")
        String password,

        @NotBlank(message = "fullName is required")
        @Size(max = 255, message = "fullName must be at most 255 characters")
        String fullName,

        LocalDate dob,

        LocalDate joinDate,

        UserStatus status
) {
}
