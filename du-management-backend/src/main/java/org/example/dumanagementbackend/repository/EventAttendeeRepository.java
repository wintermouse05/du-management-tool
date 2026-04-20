package org.example.dumanagementbackend.repository;

import org.example.dumanagementbackend.entity.EventAttendee;
import org.example.dumanagementbackend.entity.EventAttendeeId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventAttendeeRepository extends JpaRepository<EventAttendee, EventAttendeeId> {

    List<EventAttendee> findByEventId(Long eventId);
}
