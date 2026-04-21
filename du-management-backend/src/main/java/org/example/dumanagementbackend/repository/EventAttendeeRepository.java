package org.example.dumanagementbackend.repository;

import java.util.List;

import org.example.dumanagementbackend.entity.EventAttendee;
import org.example.dumanagementbackend.entity.EventAttendeeId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventAttendeeRepository extends JpaRepository<EventAttendee, EventAttendeeId> {

    List<EventAttendee> findByEventId(Long eventId);

    Page<EventAttendee> findByEventId(Long eventId, Pageable pageable);
}
