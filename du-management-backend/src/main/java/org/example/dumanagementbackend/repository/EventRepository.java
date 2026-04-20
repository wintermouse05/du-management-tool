package org.example.dumanagementbackend.repository;

import org.example.dumanagementbackend.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {
}
