package org.example.dumanagementbackend.service;

import org.example.dumanagementbackend.dto.event.EventAttendanceRequest;
import org.example.dumanagementbackend.dto.event.EventAttendeeResponse;
import org.example.dumanagementbackend.dto.event.EventRequest;
import org.example.dumanagementbackend.dto.event.EventResponse;
import org.example.dumanagementbackend.entity.Event;
import org.example.dumanagementbackend.entity.EventAttendee;
import org.example.dumanagementbackend.entity.EventAttendeeId;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.enums.RsvpStatus;
import org.example.dumanagementbackend.exception.BadRequestException;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.EventAttendeeRepository;
import org.example.dumanagementbackend.repository.EventRepository;
import org.example.dumanagementbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final EventAttendeeRepository eventAttendeeRepository;
    private final UserRepository userRepository;

    @Transactional
    public EventResponse create(EventRequest request) {
        Event event = new Event();
        apply(event, request);
        return toResponse(eventRepository.save(event));
    }

    public Page<EventResponse> getAll(Pageable pageable) {
        return eventRepository.findAll(pageable).map(this::toResponse);
    }

    public EventResponse getById(Long id) {
        return toResponse(getEntityById(id));
    }

    @Transactional
    public EventResponse update(Long id, EventRequest request) {
        Event event = getEntityById(id);
        apply(event, request);
        return toResponse(eventRepository.save(event));
    }

    @Transactional
    public EventAttendeeResponse rsvp(Long eventId, EventAttendanceRequest request) {
        if (request.userId() == null) {
            throw new BadRequestException("userId is required");
        }
        Event event = getEntityById(eventId);
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id=" + request.userId()));

        EventAttendeeId id = new EventAttendeeId();
        id.setEventId(eventId);
        id.setUserId(request.userId());

        EventAttendee attendee = eventAttendeeRepository.findById(id).orElseGet(EventAttendee::new);
        attendee.setId(id);
        attendee.setEvent(event);
        attendee.setUser(user);
        attendee.setRsvpStatus(request.rsvpStatus() != null ? request.rsvpStatus() : RsvpStatus.MAYBE);

        EventAttendee saved = eventAttendeeRepository.save(attendee);
        return toAttendeeResponse(saved);
    }

    @Transactional
    public EventAttendeeResponse checkIn(Long eventId, Long userId) {
        Event event = getEntityById(eventId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id=" + userId));

        EventAttendeeId id = new EventAttendeeId();
        id.setEventId(eventId);
        id.setUserId(userId);

        EventAttendee attendee = eventAttendeeRepository.findById(id).orElseGet(EventAttendee::new);
        attendee.setId(id);
        attendee.setEvent(event);
        attendee.setUser(user);
        attendee.setRsvpStatus(attendee.getRsvpStatus() != null ? attendee.getRsvpStatus() : RsvpStatus.YES);
        attendee.setCheckedIn(true);

        return toAttendeeResponse(eventAttendeeRepository.save(attendee));
    }

    public Page<EventAttendeeResponse> getAttendees(Long eventId, Pageable pageable) {
        return eventAttendeeRepository.findByEventId(eventId, pageable).map(this::toAttendeeResponse);
    }

    public Event getEntityById(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id=" + id));
    }

    private void apply(Event event, EventRequest request) {
        event.setName(request.name());
        event.setEventDate(request.eventDate());
        event.setLocation(request.location());
    }

    private EventResponse toResponse(Event event) {
        return new EventResponse(event.getId(), event.getName(), event.getEventDate(), event.getLocation());
    }

    private EventAttendeeResponse toAttendeeResponse(EventAttendee attendee) {
        return new EventAttendeeResponse(
                attendee.getEvent().getId(),
                attendee.getUser().getId(),
                attendee.getUser().getFullName(),
                attendee.getRsvpStatus(),
                attendee.isCheckedIn()
        );
    }
}
