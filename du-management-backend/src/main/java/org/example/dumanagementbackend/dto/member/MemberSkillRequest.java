package org.example.dumanagementbackend.dto.member;

import org.example.dumanagementbackend.entity.enums.MemberSkillType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MemberSkillRequest(
        @NotNull(message = "skill is required")
        MemberSkillType skill,

        @NotNull(message = "skill level is required")
        @Min(value = 1, message = "skill level must be between 1 and 5")
        @Max(value = 5, message = "skill level must be between 1 and 5")
        Integer level
) {
}
