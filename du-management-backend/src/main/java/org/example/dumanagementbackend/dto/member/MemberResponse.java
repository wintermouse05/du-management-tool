package org.example.dumanagementbackend.dto.member;

import org.example.dumanagementbackend.entity.enums.UserStatus;
import java.time.LocalDate;
import java.util.List;

public record MemberResponse(
        Long id,
        Long roleId,
        String roleName,
        String username,
        String email,
        String fullName,
        LocalDate dob,
        LocalDate joinDate,
        Long tenureMonths,
        Integer totalPoints,
        UserStatus status,
        List<MemberSkillResponse> skills
) {
}
