package org.example.dumanagementbackend.entity;

import java.time.LocalDateTime;

import org.example.dumanagementbackend.entity.enums.ProjectRole;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "project_members")
@Getter
@Setter
@NoArgsConstructor
public class ProjectMember extends AuditableEntity {

    @EmbeddedId
    private ProjectMemberId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("projectId")
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_role", nullable = false, length = 40)
    private ProjectRole projectRole;

    @Column(name = "participation_start_time", nullable = false)
    private LocalDateTime participationStartTime;

    @Column(name = "expected_end_time", nullable = false)
    private LocalDateTime expectedEndTime;
}
