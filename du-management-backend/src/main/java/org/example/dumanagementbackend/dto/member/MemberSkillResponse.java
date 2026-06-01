package org.example.dumanagementbackend.dto.member;

import org.example.dumanagementbackend.entity.enums.MemberSkillType;

public record MemberSkillResponse(
        MemberSkillType skill,
        String skillLabel,
        Integer level
) {
}
