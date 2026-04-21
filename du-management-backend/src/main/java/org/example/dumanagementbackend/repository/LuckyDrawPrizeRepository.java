package org.example.dumanagementbackend.repository;

import java.util.List;

import org.example.dumanagementbackend.entity.LuckyDrawPrize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LuckyDrawPrizeRepository extends JpaRepository<LuckyDrawPrize, Long> {

    List<LuckyDrawPrize> findBySessionId(Long sessionId);

    Page<LuckyDrawPrize> findBySessionId(Long sessionId, Pageable pageable);
}
