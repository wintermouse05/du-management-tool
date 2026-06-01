package org.example.dumanagementbackend.repository;

import org.example.dumanagementbackend.entity.UserOrder;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserOrderRepository extends JpaRepository<UserOrder, Long> {

    List<UserOrder> findBySessionId(Long sessionId);

    Page<UserOrder> findBySessionId(Long sessionId, Pageable pageable);

    List<UserOrder> findByUserId(Long userId);

    boolean existsBySessionIdAndUserId(Long sessionId, Long userId);

    List<UserOrder> findBySessionIdAndUserIdIn(Long sessionId, Collection<Long> userIds);

    boolean existsByItemId(Long itemId);
}
