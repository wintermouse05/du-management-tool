package org.example.dumanagementbackend.repository;

import java.time.LocalDate;
import java.util.List;

import org.example.dumanagementbackend.entity.LateRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LateRecordRepository extends JpaRepository<LateRecord, Long> {

    List<LateRecord> findByUserId(Long userId);

    Page<LateRecord> findByUserId(Long userId, Pageable pageable);

    List<LateRecord> findByRecordDateBetween(LocalDate startDate, LocalDate endDate);

    Page<LateRecord> findByRecordDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);
}
