package org.example.dumanagementbackend.dto.account;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountProfileUpdateRequest(
        @NotBlank(message = "fullName is required")
        @Size(max = 255, message = "fullName must be at most 255 characters")
        String fullName,

        LocalDate dob,

        LocalDate joinDate
) {
}
