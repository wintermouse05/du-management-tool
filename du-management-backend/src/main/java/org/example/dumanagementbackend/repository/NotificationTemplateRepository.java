package org.example.dumanagementbackend.repository;

import java.util.List;
import java.util.Optional;
import org.example.dumanagementbackend.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    Optional<NotificationTemplate> findByCode(String code);

    List<NotificationTemplate> findAllByOrderByCodeAsc();

    void deleteByCode(String code);
}