package org.example.dumanagementbackend.controller;

import org.example.dumanagementbackend.dto.event.EventAttendanceRequest;
import org.example.dumanagementbackend.dto.event.EventAttendeeResponse;
import org.example.dumanagementbackend.dto.event.EventRequest;
import org.example.dumanagementbackend.dto.event.EventResponse;
import org.example.dumanagementbackend.service.EventService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','HR','MEMBER')")
public class EventController {

    private final EventService eventService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<EventResponse> create(@RequestBody EventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<EventResponse>> getAll() {
        return ResponseEntity.ok(eventService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<EventResponse> update(@PathVariable Long id, @RequestBody EventRequest request) {
        return ResponseEntity.ok(eventService.update(id, request));
    }

    @PostMapping("/{id}/rsvp")
    public ResponseEntity<EventAttendeeResponse> rsvp(@PathVariable Long id, @RequestBody EventAttendanceRequest request) {
        return ResponseEntity.ok(eventService.rsvp(id, request));
    }

    @PostMapping("/{id}/check-in")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<EventAttendeeResponse> checkIn(@PathVariable Long id, @RequestParam Long userId) {
        return ResponseEntity.ok(eventService.checkIn(id, userId));
    }

    @GetMapping("/{id}/attendees")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<List<EventAttendeeResponse>> attendees(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getAttendees(id));
    }
}
