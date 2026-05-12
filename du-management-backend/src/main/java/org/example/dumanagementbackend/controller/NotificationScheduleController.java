package org.example.dumanagementbackend.controller;

import java.util.List;
import java.util.Map;

import org.example.dumanagementbackend.entity.NotificationSchedule;
import org.example.dumanagementbackend.entity.enums.NotificationScheduleType;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.service.ChatopsNotificationService;
import org.example.dumanagementbackend.service.NotificationScheduleService;
import org.example.dumanagementbackend.service.ScheduleManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/notification-schedules")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@ConditionalOnProperty(name = "chatops.enabled", havingValue = "true")
public class NotificationScheduleController {

    private final NotificationScheduleService scheduleService;
    private final ScheduleManager scheduleManager;
    private final ChatopsNotificationService chatopsNotificationService;

    // ---- test endpoint ----

    @PostMapping("/test/chat")
    public ResponseEntity<Map<String, String>> testChatMessage(@RequestParam(defaultValue = "Hello from DU bot — this is a test message!") String message) {
        String postId = chatopsNotificationService.sendToChannel(message);
        return ResponseEntity.ok(Map.of("status", "ok", "postId", postId != null ? postId : "null"));
    }

    @GetMapping
    public ResponseEntity<List<NotificationSchedule>> getAll() {
        return ResponseEntity.ok(scheduleService.getAll());
    }

    @GetMapping("/{type}")
    public ResponseEntity<NotificationSchedule> getByType(@PathVariable NotificationScheduleType type) {
        return ResponseEntity.ok(scheduleService.getByType(type));
    }

    @PutMapping("/{type}")
    public ResponseEntity<NotificationSchedule> upsert(
            @PathVariable NotificationScheduleType type,
            @Valid @RequestBody NotificationSchedule input) {
        NotificationSchedule saved = scheduleService.upsert(type, input);
        scheduleManager.updateSchedule(type);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        var schedule = scheduleService.getAll().stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Notification schedule not found with id=" + id));
        scheduleManager.cancelSchedule(schedule.getType());
        scheduleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
