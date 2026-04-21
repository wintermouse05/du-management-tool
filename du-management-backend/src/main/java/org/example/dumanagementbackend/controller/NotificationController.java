package org.example.dumanagementbackend.controller;

import java.util.List;
import java.util.Map;

import org.example.dumanagementbackend.dto.notification.NotificationChannelRequest;
import org.example.dumanagementbackend.dto.notification.NotificationChannelResponse;
import org.example.dumanagementbackend.dto.notification.NotificationInboxResponse;
import org.example.dumanagementbackend.dto.notification.NotificationJobResponse;
import org.example.dumanagementbackend.dto.notification.NotificationJobToggleRequest;
import org.example.dumanagementbackend.dto.notification.NotificationTemplateRequest;
import org.example.dumanagementbackend.dto.notification.NotificationTemplateResponse;
import org.example.dumanagementbackend.dto.notification.NotificationUnreadCountResponse;
import org.example.dumanagementbackend.service.NotificationChannelService;
import org.example.dumanagementbackend.service.NotificationJobService;
import org.example.dumanagementbackend.service.NotificationService;
import org.example.dumanagementbackend.service.NotificationTemplateService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationJobService notificationJobService;
    private final NotificationTemplateService notificationTemplateService;
    private final NotificationChannelService notificationChannelService;

    @GetMapping("/jobs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<NotificationJobResponse>> getJobs() {
        return ResponseEntity.ok(notificationJobService.getJobs());
    }

    @PatchMapping("/jobs/{code}/enabled")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NotificationJobResponse> setJobEnabled(
            @PathVariable String code,
            @Valid @RequestBody NotificationJobToggleRequest request
    ) {
        return ResponseEntity.ok(notificationJobService.setEnabled(code, request.enabled()));
    }

    @PostMapping("/survey-reminder")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> triggerSurveyReminder(@RequestParam Long surveyId) {
        String result = notificationService.triggerSurveyReminder(surveyId);
        return ResponseEntity.ok(Map.of("message", result));
    }

    @GetMapping("/channels")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<NotificationChannelResponse>> getChannels() {
        return ResponseEntity.ok(notificationChannelService.getChannels());
    }

    @PostMapping("/channels")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NotificationChannelResponse> createChannel(@Valid @RequestBody NotificationChannelRequest request) {
        return ResponseEntity.ok(notificationChannelService.createChannel(request));
    }

    @PutMapping("/channels/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NotificationChannelResponse> updateChannel(
            @PathVariable Long id,
            @Valid @RequestBody NotificationChannelRequest request
    ) {
        return ResponseEntity.ok(notificationChannelService.updateChannel(id, request));
    }

    @DeleteMapping("/channels/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteChannel(@PathVariable Long id) {
        notificationChannelService.deleteChannel(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/templates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<NotificationTemplateResponse>> getTemplates() {
        return ResponseEntity.ok(notificationTemplateService.getTemplates());
    }

    @PostMapping("/templates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NotificationTemplateResponse> createTemplate(
            @Valid @RequestBody NotificationTemplateRequest request
    ) {
        return ResponseEntity.ok(notificationTemplateService.createTemplate(request));
    }

    @PutMapping("/templates/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NotificationTemplateResponse> updateTemplate(
            @PathVariable String code,
            @Valid @RequestBody NotificationTemplateRequest request
    ) {
        return ResponseEntity.ok(notificationTemplateService.updateTemplate(code, request));
    }

    @DeleteMapping("/templates/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteTemplate(@PathVariable String code) {
        notificationTemplateService.deleteTemplate(code);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<NotificationInboxResponse>> getMyNotifications(Pageable pageable) {
        return ResponseEntity.ok(notificationService.getMyNotifications(pageable));
    }

    @GetMapping("/me/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationUnreadCountResponse> getMyUnreadCount() {
        return ResponseEntity.ok(notificationService.getMyUnreadCount());
    }

    @PatchMapping("/me/{notificationId}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationInboxResponse> markAsRead(@PathVariable Long notificationId) {
        return ResponseEntity.ok(notificationService.markAsRead(notificationId));
    }

    @PatchMapping("/me/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationUnreadCountResponse> markAllAsRead() {
        return ResponseEntity.ok(notificationService.markAllAsRead());
    }
}
