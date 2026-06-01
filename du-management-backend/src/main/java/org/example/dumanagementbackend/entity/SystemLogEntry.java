package org.example.dumanagementbackend.entity;

import java.time.LocalDateTime;

import org.example.dumanagementbackend.entity.enums.SystemLogCategory;
import org.example.dumanagementbackend.entity.enums.SystemLogSeverity;
import org.example.dumanagementbackend.entity.enums.SystemLogStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "system_log_entries",
        indexes = {
                @Index(name = "idx_system_logs_occurred_at", columnList = "occurred_at"),
                @Index(name = "idx_system_logs_category", columnList = "category"),
                @Index(name = "idx_system_logs_severity", columnList = "severity"),
                @Index(name = "idx_system_logs_status", columnList = "status"),
                @Index(name = "idx_system_logs_actor", columnList = "actor_username"),
                @Index(name = "idx_system_logs_correlation", columnList = "correlation_id"),
                @Index(name = "idx_system_logs_source", columnList = "source")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class SystemLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SystemLogCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SystemLogSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SystemLogStatus status;

    @Column(length = 120)
    private String action;

    @Column(length = 200)
    private String source;

    @Column(name = "actor_username", length = 100)
    private String actorUsername;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "target_type", length = 120)
    private String targetType;

    @Column(name = "target_id", length = 120)
    private String targetId;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(length = 2000)
    private String message;

    @Lob
    @Column(name = "details_json")
    private String detailsJson;

    @Column(name = "exception_class", length = 255)
    private String exceptionClass;

    @Lob
    @Column(name = "stack_trace")
    private String stackTrace;
}
