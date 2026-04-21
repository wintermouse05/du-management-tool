package org.example.dumanagementbackend.repository;

import java.util.List;
import java.util.Optional;
import org.example.dumanagementbackend.entity.NotificationJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationJobRepository extends JpaRepository<NotificationJob, Long> {

    Optional<NotificationJob> findByCode(String code);

    List<NotificationJob> findAllByOrderByCodeAsc();

    List<NotificationJob> findByEnabledTrueOrderByCodeAsc();
}