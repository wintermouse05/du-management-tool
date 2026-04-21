package org.example.dumanagementbackend.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.example.dumanagementbackend.entity.Survey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SurveyRepository extends JpaRepository<Survey, Long> {

	List<Survey> findByDeadlineBetween(LocalDateTime from, LocalDateTime to);
}
