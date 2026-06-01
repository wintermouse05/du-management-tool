package org.example.dumanagementbackend.repository;

import java.time.LocalDateTime;

import org.example.dumanagementbackend.entity.SystemLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SystemLogRepository extends JpaRepository<SystemLogEntry, Long>, JpaSpecificationExecutor<SystemLogEntry> {

    @Modifying
    @Query("delete from SystemLogEntry l where l.occurredAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
