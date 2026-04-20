package org.example.dumanagementbackend.repository;

import org.example.dumanagementbackend.entity.OrderSession;
import org.example.dumanagementbackend.entity.enums.OrderSessionStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderSessionRepository extends JpaRepository<OrderSession, Long> {

    List<OrderSession> findByStatus(OrderSessionStatus status);
}
