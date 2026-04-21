package org.example.dumanagementbackend.entity;

import org.example.dumanagementbackend.entity.enums.SeminarStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "seminars")
@Getter
@Setter
@NoArgsConstructor
public class Seminar extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "speaker_id")
    private User speaker;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "materials_url", length = 500)
    private String materialsUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeminarStatus status = SeminarStatus.PROPOSED;
}
