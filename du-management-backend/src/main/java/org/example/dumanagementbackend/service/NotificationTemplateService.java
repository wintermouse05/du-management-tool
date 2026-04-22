package org.example.dumanagementbackend.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.dumanagementbackend.dto.notification.NotificationTemplateRequest;
import org.example.dumanagementbackend.dto.notification.NotificationTemplateResponse;
import org.example.dumanagementbackend.entity.NotificationTemplate;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.NotificationTemplateRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationTemplateService {

    public static final String TPL_BIRTHDAY_ANNIVERSARY = "TPL_BIRTHDAY_ANNIVERSARY";
    public static final String TPL_EVENT_REMINDER = "TPL_EVENT_REMINDER";
    public static final String TPL_SURVEY_REMINDER = "TPL_SURVEY_REMINDER";

    private final NotificationTemplateRepository notificationTemplateRepository;

    @Transactional
    public List<NotificationTemplateResponse> getTemplates() {
        ensureDefaultTemplates();
        return notificationTemplateRepository.findAllByOrderByCodeAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    @CacheEvict(cacheNames = "notificationTemplateByCode", allEntries = true)
    public NotificationTemplateResponse createTemplate(NotificationTemplateRequest request) {
        NotificationTemplate template = new NotificationTemplate();
        apply(template, request);
        return toResponse(notificationTemplateRepository.save(template));
    }

    @Transactional
    @CacheEvict(cacheNames = "notificationTemplateByCode", allEntries = true)
    public NotificationTemplateResponse updateTemplate(String code, NotificationTemplateRequest request) {
        NotificationTemplate template = notificationTemplateRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Notification template not found with code=" + code));
        apply(template, request);
        return toResponse(notificationTemplateRepository.save(template));
    }

    @Transactional
    @CacheEvict(cacheNames = "notificationTemplateByCode", allEntries = true)
    public void deleteTemplate(String code) {
        NotificationTemplate template = notificationTemplateRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Notification template not found with code=" + code));
        notificationTemplateRepository.delete(template);
    }

    @Transactional
    public void ensureDefaultTemplates() {
        ensureTemplate(
                TPL_BIRTHDAY_ANNIVERSARY,
                "Birthday and Anniversary",
                "Celebration for {name}",
                "Today is {name}'s {occasion}. Let's send best wishes!",
                true
        );
        ensureTemplate(
                TPL_EVENT_REMINDER,
                "Event Reminder",
                "Event starts in 1 hour: {eventName}",
                "Reminder: {eventName} starts at {eventTime} in {location}.",
                true
        );
        ensureTemplate(
                TPL_SURVEY_REMINDER,
                "Survey Reminder",
                "Survey deadline is approaching: {surveyTitle}",
                "Please complete survey \"{surveyTitle}\" before {deadline}.",
                true
        );
    }

    public RenderedTemplate renderTemplate(
            String code,
            Map<String, String> placeholders,
            String fallbackSubject,
            String fallbackBody
    ) {
        Optional<NotificationTemplate> optionalTemplate = notificationTemplateRepository.findByCode(code)
                .filter(NotificationTemplate::isEnabled);

        String subject = optionalTemplate.map(NotificationTemplate::getSubjectTemplate).orElse(fallbackSubject);
        String body = optionalTemplate.map(NotificationTemplate::getBodyTemplate).orElse(fallbackBody);

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String token = "{" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            subject = subject.replace(token, value);
            body = body.replace(token, value);
        }

        return new RenderedTemplate(subject, body);
    }

    private void ensureTemplate(String code, String name, String subject, String body, boolean enabled) {
        if (notificationTemplateRepository.findByCode(code).isPresent()) {
            return;
        }

        NotificationTemplate template = new NotificationTemplate();
        template.setCode(code);
        template.setName(name);
        template.setSubjectTemplate(subject);
        template.setBodyTemplate(body);
        template.setEnabled(enabled);
        notificationTemplateRepository.save(template);
    }

    private void apply(NotificationTemplate template, NotificationTemplateRequest request) {
        template.setCode(request.code().trim());
        template.setName(request.name().trim());
        template.setSubjectTemplate(request.subjectTemplate().trim());
        template.setBodyTemplate(request.bodyTemplate().trim());
        template.setEnabled(request.enabled());
    }

    private NotificationTemplateResponse toResponse(NotificationTemplate template) {
        return new NotificationTemplateResponse(
                template.getId(),
                template.getCode(),
                template.getName(),
                template.getSubjectTemplate(),
                template.getBodyTemplate(),
                template.isEnabled(),
                template.getUpdatedAt()
        );
    }

    public record RenderedTemplate(
            String subject,
            String body
    ) {
    }
}