package org.example.dumanagementbackend.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.example.dumanagementbackend.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {

	List<Event> findByEventDateBetween(LocalDateTime from, LocalDateTime to);
}
