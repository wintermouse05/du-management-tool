package org.example.dumanagementbackend.repository;

import org.example.dumanagementbackend.entity.LateRecord;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LateRecordRepository extends JpaRepository<LateRecord, Long> {

    List<LateRecord> findByUserId(Long userId);

    List<LateRecord> findByRecordDateBetween(LocalDate startDate, LocalDate endDate);
}
