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
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class EventService {

    private static final long IMMEDIATE_REMINDER_DAYS = 3L;

    private final EventRepository eventRepository;
    private final EventAttendeeRepository eventAttendeeRepository;
    private final UserRepository userRepository;
    private final GamificationService gamificationService;
    private final NotificationService notificationService;

    @Autowired(required = false)
    private ChatopsNotificationService chatopsNotificationService;

    @Transactional
    public EventResponse create(EventRequest request) {
        Event event = new Event();
        apply(event, request);
        Event saved = eventRepository.save(event);
        triggerCreatedNotification(saved);
        triggerImmediateReminderIfNeeded(saved);
        return toResponse(saved, resolveCreatorNames(List.of(saved)));
    }

    public Page<EventResponse> getAll(Pageable pageable) {
        Pageable resolvedPageable = withDefaultSort(pageable);
        Page<Event> page = eventRepository.findAll(resolvedPageable);
        Map<String, String> creatorNames = resolveCreatorNames(page.getContent());
        List<EventResponse> responses = page.getContent().stream()
                .map(event -> toResponse(event, creatorNames))
                .toList();
        return new PageImpl<>(responses, resolvedPageable, page.getTotalElements());
    }

    public EventResponse getById(Long id) {
        Event event = getEntityById(id);
        return toResponse(event, resolveCreatorNames(List.of(event)));
    }

    @Transactional
    public EventResponse update(Long id, EventRequest request) {
        Event event = getEntityById(id);
        User currentUser = getCurrentAuthenticatedUser();
        if (!canEditEvent(currentUser, event)) {
            throw new AccessDeniedException("You do not have permission to edit this event");
        }
        apply(event, request);
        Event saved = eventRepository.save(event);
        return toResponse(saved, resolveCreatorNames(List.of(saved)));
    }

    @Transactional
    public EventAttendeeResponse rsvp(Long eventId, EventAttendanceRequest request) {
        Event event = getEntityById(eventId);
        
        if (event.getEventDate() != null && java.time.LocalDateTime.now().isAfter(event.getEventDate())) {
            throw new BadRequestException("Cannot RSVP to an event that has already occurred.");
        }
        
        User user = getCurrentAuthenticatedUser();

        EventAttendeeId id = new EventAttendeeId();
        id.setEventId(eventId);
        id.setUserId(user.getId());

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
        boolean alreadyCheckedIn = attendee.isCheckedIn();
        attendee.setId(id);
        attendee.setEvent(event);
        attendee.setUser(user);
        attendee.setRsvpStatus(attendee.getRsvpStatus() != null ? attendee.getRsvpStatus() : RsvpStatus.YES);
        attendee.setCheckedIn(true);

        if (!alreadyCheckedIn) {
            gamificationService.applyActionPoints(userId, "EVENT_ATTENDANCE", "Checked in to event: " + event.getName());
        }

        return toAttendeeResponse(eventAttendeeRepository.save(attendee));
    }

    public Page<EventAttendeeResponse> getAttendees(Long eventId, Pageable pageable) {
        Pageable resolvedPageable = PaginationUtils.toZeroBasedPageable(pageable);
        return eventAttendeeRepository.findByEventId(eventId, resolvedPageable).map(this::toAttendeeResponse);
    }

    public List<EventAttendeeResponse> getMyAttendances() {
        User currentUser = getCurrentAuthenticatedUser();
        return eventAttendeeRepository.findByIdUserId(currentUser.getId()).stream()
                .map(this::toAttendeeResponse)
                .toList();
    }

    public Event getEntityById(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id=" + id));
    }

    @Transactional
    public String triggerEventReminder(Long eventId) {
        return notificationService.triggerEventReminder(eventId);
    }

    private void apply(Event event, EventRequest request) {
        event.setName(request.name());
        event.setEventDate(request.eventDate());
        event.setLocation(request.location());
        event.setDescription(request.description());
    }

    private void triggerImmediateReminderIfNeeded(Event event) {
        if (event.getId() == null || event.getEventDate() == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reminderThreshold = now.plusDays(IMMEDIATE_REMINDER_DAYS);
        if (event.getEventDate().isAfter(now) && !event.getEventDate().isAfter(reminderThreshold)) {
            Long eventId = event.getId();
            dispatchAfterCommit(
                    "Immediate reminder dispatch for eventId=" + eventId,
                    () -> notificationService.triggerEventReminder(eventId)
            );
        }
    }

    private void triggerCreatedNotification(Event event) {
        ChatopsNotificationService notifier = chatopsNotificationService;
        if (notifier == null || event == null) {
            return;
        }

        Long eventId = event.getId();
        String name = event.getName();
        LocalDateTime eventDate = event.getEventDate();
        String location = event.getLocation();
        String description = event.getDescription();
        dispatchAfterCommit(
                "Event created ChatOps notification",
                () -> notifier.sendEventCreatedNotification(eventId, name, eventDate, location, description)
        );
    }

    private void dispatchAfterCommit(String taskName, Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    CompletableFuture.runAsync(() -> runBestEffort(taskName, task));
                }
            });
            return;
        }

        runBestEffort(taskName, task);
    }

    private void runBestEffort(String taskName, Runnable task) {
        try {
            task.run();
        } catch (Exception ex) {
            log.warn("{} failed: {}", taskName, ex.getMessage());
        }
    }

    private EventResponse toResponse(Event event, Map<String, String> creatorNames) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getEventDate(),
                event.getLocation(),
                event.getDescription(),
                resolveCreatorDisplayName(event.getCreatedBy(), creatorNames),
                event.getCreatedBy()
        );
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

    private User getCurrentAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BadRequestException("Authenticated user was not found in security context");
        }

        return userRepository.findByUsername(authentication.getName())
            .or(() -> userRepository.findByEmail(authentication.getName()))
                .orElseThrow(() -> new ResourceNotFoundException("User not found for username=" + authentication.getName()));
    }

    private Map<String, String> resolveCreatorNames(List<Event> events) {
        Set<String> usernames = events.stream()
                .map(Event::getCreatedBy)
                .filter(username -> username != null && !username.isBlank() && !"system".equalsIgnoreCase(username))
                .collect(Collectors.toSet());
        if (usernames.isEmpty()) {
            return Map.of();
        }

        Map<String, String> creatorNames = new HashMap<>();
        userRepository.findByUsernameIn(usernames)
                .forEach(user -> creatorNames.put(
                        user.getUsername(),
                        user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getUsername()
                ));
        return creatorNames;
    }

    private String resolveCreatorDisplayName(String creatorUsername, Map<String, String> creatorNames) {
        if (creatorUsername == null || creatorUsername.isBlank()) {
            return "system";
        }
        return creatorNames.getOrDefault(creatorUsername, creatorUsername);
    }

    private boolean canEditEvent(User currentUser, Event event) {
        if (currentUser == null || event == null) {
            return false;
        }

        String roleName = currentUser.getRole() != null ? currentUser.getRole().getName() : null;
        if ("ADMIN".equalsIgnoreCase(roleName)) {
            return true;
        }

        String createdBy = event.getCreatedBy();
        if (createdBy == null || createdBy.isBlank()) {
            return false;
        }

        return createdBy.equalsIgnoreCase(currentUser.getUsername())
                || createdBy.equalsIgnoreCase(currentUser.getEmail());
    }

    private Pageable withDefaultSort(Pageable pageable) {
        Sort defaultSort = Sort.by(Sort.Order.desc("eventDate"), Sort.Order.desc("id"));
        if (pageable == null) {
            return PageRequest.of(0, 20, defaultSort);
        }
        if (pageable.isUnpaged()) {
            return pageable;
        }

        Pageable resolvedPageable = PaginationUtils.toZeroBasedPageable(pageable);
        return resolvedPageable.getSort().isSorted()
                ? resolvedPageable
                : PageRequest.of(resolvedPageable.getPageNumber(), resolvedPageable.getPageSize(), defaultSort);
    }
}
