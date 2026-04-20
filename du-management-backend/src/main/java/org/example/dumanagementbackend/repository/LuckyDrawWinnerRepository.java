package org.example.dumanagementbackend.repository;

import org.example.dumanagementbackend.entity.LuckyDrawWinner;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LuckyDrawWinnerRepository extends JpaRepository<LuckyDrawWinner, Long> {

    List<LuckyDrawWinner> findByPrizeId(Long prizeId);
}
