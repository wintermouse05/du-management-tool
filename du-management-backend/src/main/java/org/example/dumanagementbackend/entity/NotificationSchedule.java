package org.example.dumanagementbackend.entity;

import java.time.LocalTime;

import org.example.dumanagementbackend.entity.enums.NotificationScheduleType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notification_schedules")
@Getter
@Setter
@NoArgsConstructor
public class NotificationSchedule extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationScheduleType type;

    @Column(name = "send_time", nullable = false)
    private LocalTime sendTime;

    @Column(name = "channel_id", length = 100)
    private String channelId;

    @Column(name = "chatops_post_id", length = 100)
    private String chatopsPostId;

    @Column(nullable = false)
    private boolean enabled = true;
}
