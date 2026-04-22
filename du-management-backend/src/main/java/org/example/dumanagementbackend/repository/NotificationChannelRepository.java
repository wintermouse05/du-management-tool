package org.example.dumanagementbackend.repository;

import java.util.List;

import org.example.dumanagementbackend.entity.NotificationChannel;
import org.example.dumanagementbackend.entity.enums.NotificationChannelType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationChannelRepository extends JpaRepository<NotificationChannel, Long> {

    @Cacheable(cacheNames = "notificationEnabledChannels")
    List<NotificationChannel> findByEnabledTrueOrderByTypeAscIdAsc();

    List<NotificationChannel> findByTypeAndEnabledTrueOrderByIdAsc(NotificationChannelType type);
}
