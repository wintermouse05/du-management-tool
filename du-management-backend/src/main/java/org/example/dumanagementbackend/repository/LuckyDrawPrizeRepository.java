package org.example.dumanagementbackend.repository;

import org.example.dumanagementbackend.entity.LuckyDrawPrize;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LuckyDrawPrizeRepository extends JpaRepository<LuckyDrawPrize, Long> {

    List<LuckyDrawPrize> findBySessionId(Long sessionId);
}
