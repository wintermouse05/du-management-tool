package org.example.dumanagementbackend.repository;

import org.example.dumanagementbackend.entity.LuckyDrawSession;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LuckyDrawSessionRepository extends JpaRepository<LuckyDrawSession, Long> {

    List<LuckyDrawSession> findByEventId(Long eventId);
}
