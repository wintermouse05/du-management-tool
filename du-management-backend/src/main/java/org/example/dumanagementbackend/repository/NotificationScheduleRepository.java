package org.example.dumanagementbackend.repository;

import java.util.List;
import java.util.Optional;

import org.example.dumanagementbackend.entity.NotificationSchedule;
import org.example.dumanagementbackend.entity.enums.NotificationScheduleType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationScheduleRepository extends JpaRepository<NotificationSchedule, Long> {

    Optional<NotificationSchedule> findByType(NotificationScheduleType type);

    List<NotificationSchedule> findByEnabledTrue();
}
