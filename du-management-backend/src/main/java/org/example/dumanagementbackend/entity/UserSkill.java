package org.example.dumanagementbackend.entity;

import org.example.dumanagementbackend.entity.enums.MemberSkillType;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSkill {

    @Enumerated(EnumType.STRING)
    @Column(name = "skill", nullable = false, length = 60)
    private MemberSkillType skill;

    @Column(name = "skill_level", nullable = false)
    private Integer level;
}
