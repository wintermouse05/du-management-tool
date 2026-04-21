package org.example.dumanagementbackend.entity;

import org.example.dumanagementbackend.entity.enums.NotificationChannelType;

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
@Table(name = "notification_channels")
@Getter
@Setter
@NoArgsConstructor
public class NotificationChannel extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannelType type;

    @Column(nullable = false, length = 500)
    private String endpoint;

    @Column(nullable = false)
    private boolean enabled = true;
}
