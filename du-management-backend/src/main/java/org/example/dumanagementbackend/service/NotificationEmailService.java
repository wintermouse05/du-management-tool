package org.example.dumanagementbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dumanagementbackend.entity.enums.SystemLogCategory;
import org.example.dumanagementbackend.entity.enums.SystemLogSeverity;
import org.example.dumanagementbackend.entity.enums.SystemLogStatus;
import org.example.dumanagementbackend.logging.SystemLogContext;
import org.example.dumanagementbackend.logging.SystemLogSanitizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEmailService {

    private final JavaMailSender javaMailSender;
    private final SystemLogService systemLogService;

    @Value("${app.notification.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.notification.email.from:no-reply@du-manager.local}")
    private String fromAddress;

    public void sendEmail(String to, String subject, String body) {
        if (!emailEnabled || to == null || to.isBlank()) {
            logEmail(to, subject, SystemLogStatus.SKIPPED, SystemLogSeverity.INFO, null);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            javaMailSender.send(message);
            logEmail(to, subject, SystemLogStatus.SUCCESS, SystemLogSeverity.INFO, null);
        } catch (Exception ex) {
            log.warn("Failed to send notification email to {}: {}", to, ex.getMessage());
            logEmail(to, subject, SystemLogStatus.FAILED, SystemLogSeverity.ERROR, ex);
        }
    }

    private void logEmail(String to, String subject, SystemLogStatus status, SystemLogSeverity severity, Throwable failure) {
        java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("to", to);
        details.put("subject", subject);
        details.put("emailEnabled", emailEnabled);

        systemLogService.log(new SystemLogCreateRequest(
                SystemLogCategory.MESSAGE,
                severity,
                status,
                "EMAIL",
                "NotificationEmailService",
                SystemLogContext.getActorUsername(),
                SystemLogContext.getCorrelationId(),
                "Email",
                to,
                null,
                status == SystemLogStatus.SKIPPED
                        ? "Email notification skipped"
                        : "Email notification " + status.name().toLowerCase(),
                details,
                failure != null ? failure.getClass().getName() : null,
                SystemLogSanitizer.stackTrace(failure)
        ));
    }
}
