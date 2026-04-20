package org.example.dumanagementbackend.dto.member;

import org.example.dumanagementbackend.entity.enums.UserStatus;
import java.time.LocalDate;

public record MemberRequest(
        Long roleId,
        String username,
        String email,
        String password,
        String fullName,
        LocalDate dob,
        LocalDate joinDate,
        UserStatus status
) {
}
