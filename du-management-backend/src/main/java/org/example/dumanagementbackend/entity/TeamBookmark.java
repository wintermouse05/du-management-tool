package org.example.dumanagementbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "team_bookmarks",
        indexes = {
                @Index(name = "idx_team_bookmarks_pinned", columnList = "pinned"),
                @Index(name = "idx_team_bookmarks_category", columnList = "category")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class TeamBookmark extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(length = 1000)
    private String description;

    @Column(length = 120)
    private String category;

    @Column(nullable = false)
    private boolean pinned;
}
