package org.example.dumanagementbackend.controller;

import org.example.dumanagementbackend.dto.notification.NotificationJobResponse;
import org.example.dumanagementbackend.service.NotificationService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/jobs")
    public ResponseEntity<List<NotificationJobResponse>> getJobs() {
        return ResponseEntity.ok(notificationService.getSystemJobs());
    }

    @PostMapping("/survey-reminder")
    public ResponseEntity<Map<String, String>> triggerSurveyReminder(@RequestParam Long surveyId) {
        String result = notificationService.triggerSurveyReminder(surveyId);
        return ResponseEntity.ok(Map.of("message", result));
    }
}
