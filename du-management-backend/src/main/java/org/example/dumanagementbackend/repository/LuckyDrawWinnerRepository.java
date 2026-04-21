package org.example.dumanagementbackend.repository;

import java.util.List;

import org.example.dumanagementbackend.entity.LuckyDrawWinner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LuckyDrawWinnerRepository extends JpaRepository<LuckyDrawWinner, Long> {

    List<LuckyDrawWinner> findByPrizeId(Long prizeId);

    Page<LuckyDrawWinner> findByPrizeId(Long prizeId, Pageable pageable);

    long countByPrizeId(Long prizeId);
}
