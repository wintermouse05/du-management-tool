package org.example.dumanagementbackend.repository;

import java.util.Optional;
import org.example.dumanagementbackend.entity.ChatopsChannelConfig;
import org.example.dumanagementbackend.entity.enums.ChatopsChannelPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatopsChannelConfigRepository extends JpaRepository<ChatopsChannelConfig, Long> {

    Optional<ChatopsChannelConfig> findByPurpose(ChatopsChannelPurpose purpose);
}
