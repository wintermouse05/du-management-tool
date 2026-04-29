package org.example.dumanagementbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventAttendeeRepository eventAttendeeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GamificationService gamificationService;

    @InjectMocks
    private EventService eventService;

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void create_returnsEventResponse() {
        EventRequest req = new EventRequest("Tech Talk", LocalDateTime.now().plusDays(5), "Room 101");
        Event saved = buildEvent(1L, "Tech Talk");

        when(eventRepository.save(any(Event.class))).thenReturn(saved);

        EventResponse response = eventService.create(req);

        assertEquals(1L, response.id());
        assertEquals("Tech Talk", response.name());
        verify(eventRepository).save(any(Event.class));
    }

    // ── getAll ───────────────────────────────────────────────────────────────

    @Test
    void getAll_returnsMappedPage() {
        Pageable pageable = PageRequest.of(0, 5);
        Event e1 = buildEvent(1L, "Event One");
        Event e2 = buildEvent(2L, "Event Two");
        Page<Event> page = new PageImpl<>(List.of(e1, e2), pageable, 2);

        when(eventRepository.findAll(pageable)).thenReturn(page);

        Page<EventResponse> result = eventService.getAll(pageable);

        assertEquals(2, result.getTotalElements());
        assertEquals("Event One", result.getContent().get(0).name());
        assertEquals("Event Two", result.getContent().get(1).name());
    }

    // ── getById ──────────────────────────────────────────────────────────────

    @Test
    void getById_throwsNotFoundWhenEventMissing() {
        when(eventRepository.findById(99L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> eventService.getById(99L));
        assertEquals("Event not found with id=99", ex.getMessage());
    }

    @Test
    void getById_returnsResponseWhenFound() {
        Event event = buildEvent(3L, "Workshop");
        when(eventRepository.findById(3L)).thenReturn(Optional.of(event));

        EventResponse response = eventService.getById(3L);
        assertEquals(3L, response.id());
        assertEquals("Workshop", response.name());
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    void update_updatesAndReturnsResponse() {
        Event existing = buildEvent(5L, "Old Name");
        EventRequest req = new EventRequest("New Name", LocalDateTime.now().plusDays(1), "Hall A");

        when(eventRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        EventResponse response = eventService.update(5L, req);

        assertEquals("New Name", response.name());
        assertEquals("Hall A", response.location());
    }

    // ── rsvp ─────────────────────────────────────────────────────────────────

    @Test
    void rsvp_throwsBadRequestWhenEventAlreadyOccurred() {
        Event pastEvent = buildEvent(10L, "Past Event");
        pastEvent.setEventDate(LocalDateTime.now().minusDays(1));

        when(eventRepository.findById(10L)).thenReturn(Optional.of(pastEvent));

        EventAttendanceRequest req = new EventAttendanceRequest(99L, RsvpStatus.YES);

        assertThrows(BadRequestException.class, () -> eventService.rsvp(10L, req));
    }

    @Test
    void rsvp_defaultsToMaybeWhenRsvpStatusNull() {
        Event futureEvent = buildEvent(11L, "Future Event");
        futureEvent.setEventDate(LocalDateTime.now().plusDays(5));
        User user = buildUser(20L, "Alice");

        EventAttendeeId attendeeId = new EventAttendeeId();
        attendeeId.setEventId(11L);
        attendeeId.setUserId(20L);

        when(eventRepository.findById(11L)).thenReturn(Optional.of(futureEvent));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        SecurityContext ctx = org.mockito.Mockito.mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        when(auth.getName()).thenReturn("alice");
        SecurityContextHolder.setContext(ctx);

        when(eventAttendeeRepository.findById(any(EventAttendeeId.class))).thenReturn(Optional.empty());
        when(eventAttendeeRepository.save(any(EventAttendee.class))).thenAnswer(inv -> {
            EventAttendee ea = inv.getArgument(0);
            ea.setId(attendeeId);
            ea.setEvent(futureEvent);
            ea.setUser(user);
            return ea;
        });

        EventAttendeeResponse response = eventService.rsvp(11L, new EventAttendanceRequest(null, null));

        assertEquals(RsvpStatus.MAYBE, response.rsvpStatus());
        assertEquals(20L, response.userId());
    }

    // ── checkIn ──────────────────────────────────────────────────────────────

    @Test
    void checkIn_grantsPointsOnFirstCheckIn() {
        Event event = buildEvent(12L, "Hackathon");
        User user = buildUser(30L, "Bob");

        EventAttendeeId id = new EventAttendeeId();
        id.setEventId(12L);
        id.setUserId(30L);

        when(eventRepository.findById(12L)).thenReturn(Optional.of(event));
        when(userRepository.findById(30L)).thenReturn(Optional.of(user));
        when(eventAttendeeRepository.findById(any(EventAttendeeId.class))).thenReturn(Optional.empty());
        when(eventAttendeeRepository.save(any(EventAttendee.class))).thenAnswer(inv -> {
            EventAttendee ea = inv.getArgument(0);
            ea.setId(id);
            ea.setEvent(event);
            ea.setUser(user);
            ea.setCheckedIn(true);
            ea.setRsvpStatus(RsvpStatus.YES);
            return ea;
        });

        EventAttendeeResponse response = eventService.checkIn(12L, 30L);

        assertTrue(response.checkedIn());
        verify(gamificationService).applyActionPoints(30L, "EVENT_ATTENDANCE", "Checked in to event: Hackathon");
    }

    @Test
    void checkIn_doesNotGrantPointsWhenAlreadyCheckedIn() {
        Event event = buildEvent(12L, "Hackathon");
        User user = buildUser(30L, "Bob");

        EventAttendeeId id = new EventAttendeeId();
        id.setEventId(12L);
        id.setUserId(30L);

        EventAttendee existing = new EventAttendee();
        existing.setId(id);
        existing.setEvent(event);
        existing.setUser(user);
        existing.setCheckedIn(true);
        existing.setRsvpStatus(RsvpStatus.YES);

        when(eventRepository.findById(12L)).thenReturn(Optional.of(event));
        when(userRepository.findById(30L)).thenReturn(Optional.of(user));
        when(eventAttendeeRepository.findById(any(EventAttendeeId.class))).thenReturn(Optional.of(existing));
        when(eventAttendeeRepository.save(any(EventAttendee.class))).thenReturn(existing);

        eventService.checkIn(12L, 30L);

        verify(gamificationService, never()).applyActionPoints(any(), any(), any());
    }

    @Test
    void checkIn_throwsNotFoundWhenUserMissing() {
        Event event = buildEvent(12L, "Hackathon");
        when(eventRepository.findById(12L)).thenReturn(Optional.of(event));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> eventService.checkIn(12L, 99L));
    }

    // ── getAttendees ──────────────────────────────────────────────────────────

    @Test
    void getAttendees_returnsMappedPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Event event = buildEvent(1L, "Conf");
        User user = buildUser(5L, "Carol");

        EventAttendeeId id = new EventAttendeeId();
        id.setEventId(1L);
        id.setUserId(5L);

        EventAttendee attendee = new EventAttendee();
        attendee.setId(id);
        attendee.setEvent(event);
        attendee.setUser(user);
        attendee.setRsvpStatus(RsvpStatus.YES);
        attendee.setCheckedIn(false);

        Page<EventAttendee> page = new PageImpl<>(List.of(attendee), pageable, 1);
        when(eventAttendeeRepository.findByEventId(1L, pageable)).thenReturn(page);

        Page<EventAttendeeResponse> result = eventService.getAttendees(1L, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("Carol", result.getContent().get(0).fullName());
        assertEquals(RsvpStatus.YES, result.getContent().get(0).rsvpStatus());
        assertFalse(result.getContent().get(0).checkedIn());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Event buildEvent(Long id, String name) {
        Event event = new Event();
        event.setId(id);
        event.setName(name);
        event.setEventDate(LocalDateTime.now().plusDays(7));
        event.setLocation("Location X");
        return event;
    }

    private User buildUser(Long id, String fullName) {
        User user = new User();
        user.setId(id);
        user.setFullName(fullName);
        user.setUsername(fullName.toLowerCase());
        return user;
    }
}
