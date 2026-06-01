package org.example.dumanagementbackend.repository;

import java.time.LocalDateTime;
import org.example.dumanagementbackend.entity.OrderSession;
import org.example.dumanagementbackend.entity.enums.OrderSessionStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderSessionRepository extends JpaRepository<OrderSession, Long> {

    List<OrderSession> findByStatus(OrderSessionStatus status);

    List<OrderSession> findByStatusAndDeadlineBetween(
            OrderSessionStatus status,
            LocalDateTime from,
            LocalDateTime to
    );

    List<OrderSession> findByStatusAndDeadlineBetweenAndDeadlineReminderSentAtIsNull(
            OrderSessionStatus status,
            LocalDateTime from,
            LocalDateTime to
    );

    List<OrderSession> findByStatusAndDeadlineLessThanEqual(
            OrderSessionStatus status,
            LocalDateTime deadline
    );

    boolean existsByRestaurant_IdAndStatus(Long restaurantId, OrderSessionStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OrderSession session
               set session.deadlineReminderSentAt = :sentAt
             where session.id = :sessionId
               and session.deadlineReminderSentAt is null
            """)
    int markDeadlineReminderSentIfNeeded(@Param("sessionId") Long sessionId, @Param("sentAt") LocalDateTime sentAt);
}
