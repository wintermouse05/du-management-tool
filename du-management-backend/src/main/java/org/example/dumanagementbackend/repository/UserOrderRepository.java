package org.example.dumanagementbackend.repository;

import org.example.dumanagementbackend.entity.UserOrder;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserOrderRepository extends JpaRepository<UserOrder, Long> {

    List<UserOrder> findBySessionId(Long sessionId);

    List<UserOrder> findByUserId(Long userId);
}
