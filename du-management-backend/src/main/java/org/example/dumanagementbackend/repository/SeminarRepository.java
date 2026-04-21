package org.example.dumanagementbackend.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.example.dumanagementbackend.entity.Seminar;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeminarRepository extends JpaRepository<Seminar, Long> {

	List<Seminar> findByScheduledAtBetween(LocalDateTime from, LocalDateTime to);
}
