package org.example.dumanagementbackend.dto.account;

import java.time.LocalDate;
import org.example.dumanagementbackend.entity.enums.UserStatus;

public record AccountResponse(
        Long id,
        String username,
        String email,
        String fullName,
        String roleName,
        LocalDate dob,
        LocalDate joinDate,
        Long tenureMonths,
        Integer totalPoints,
        UserStatus status
) {
}
