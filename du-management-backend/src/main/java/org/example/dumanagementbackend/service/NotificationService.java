package org.example.dumanagementbackend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.dumanagementbackend.dto.notification.NotificationInboxResponse;
import org.example.dumanagementbackend.dto.notification.NotificationRealtimeResponse;
import org.example.dumanagementbackend.dto.notification.NotificationUnreadCountResponse;
import org.example.dumanagementbackend.entity.Event;
import org.example.dumanagementbackend.entity.EventAttendee;
import org.example.dumanagementbackend.entity.NotificationChannel;
import org.example.dumanagementbackend.entity.Notification;
import org.example.dumanagementbackend.entity.Seminar;
import org.example.dumanagementbackend.entity.Survey;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.UserSurvey;
import org.example.dumanagementbackend.entity.enums.NotificationChannelType;
import org.example.dumanagementbackend.entity.enums.NotificationType;
import org.example.dumanagementbackend.entity.enums.RsvpStatus;
import org.example.dumanagementbackend.entity.enums.UserStatus;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.EventAttendeeRepository;
import org.example.dumanagementbackend.repository.EventRepository;
import org.example.dumanagementbackend.repository.NotificationChannelRepository;
import org.example.dumanagementbackend.repository.NotificationRepository;
import org.example.dumanagementbackend.repository.SeminarRepository;
import org.example.dumanagementbackend.repository.SurveyRepository;
import org.example.dumanagementbackend.repository.UserRepository;
import org.example.dumanagementbackend.repository.UserSurveyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

        private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SimpMessagingTemplate messagingTemplate;
        private final NotificationRepository notificationRepository;
        private final UserRepository userRepository;
        private final EventRepository eventRepository;
        private final EventAttendeeRepository eventAttendeeRepository;
        private final SeminarRepository seminarRepository;
        private final SurveyRepository surveyRepository;
        private final UserSurveyRepository userSurveyRepository;
        private final NotificationTemplateService notificationTemplateService;
        private final NotificationEmailService notificationEmailService;
        private final NotificationChannelRepository notificationChannelRepository;
        private final WebClient webClient = WebClient.builder().build();

        public Page<NotificationInboxResponse> getMyNotifications(Pageable pageable) {
                User currentUser = getCurrentUser();
                return notificationRepository
                                .findByUserIdOrderByCreatedAtDesc(currentUser.getId(), pageable)
                                .map(this::toInboxResponse);
        }

        public NotificationUnreadCountResponse getMyUnreadCount() {
                User currentUser = getCurrentUser();
                long unreadCount = notificationRepository.countByUserIdAndIsReadFalse(currentUser.getId());
                return new NotificationUnreadCountResponse(unreadCount);
        }

        @Transactional
        public NotificationInboxResponse markAsRead(Long notificationId) {
                User currentUser = getCurrentUser();
                Notification notification = notificationRepository
                                .findByIdAndUserId(notificationId, currentUser.getId())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Notification not found with id=" + notificationId + " for current user"
                                ));

                if (!notification.isRead()) {
                        notification.setRead(true);
                        notification.setReadAt(LocalDateTime.now());
                        notification = notificationRepository.save(notification);
                }

                return toInboxResponse(notification);
        }

        @Transactional
        public NotificationUnreadCountResponse markAllAsRead() {
                User currentUser = getCurrentUser();
                notificationRepository.markAllAsReadByUserId(currentUser.getId());
                return new NotificationUnreadCountResponse(0L);
        }

        @Transactional
        public String triggerSurveyReminder(Long surveyId) {
                Survey survey = surveyRepository.findById(surveyId)
                                .orElseThrow(() -> new ResourceNotFoundException("Survey not found with id=" + surveyId));
                List<UserSurvey> pendingAssignments = userSurveyRepository.findBySurveyIdAndCompletedFalse(surveyId);

                if (pendingAssignments.isEmpty()) {
                        return "No pending users found for surveyId=" + surveyId;
                }

                int sentCount = 0;
                for (UserSurvey assignment : pendingAssignments) {
                        if (assignment.getUser().getStatus() != UserStatus.ACTIVE) {
                                continue;
                        }

                        sentCount += sendSurveyNotificationToUser(assignment.getUser(), survey);
                }

                return "Triggered survey reminder for surveyId=" + surveyId + " to " + sentCount + " user(s)";
        }

        @Transactional
        public int runBirthdayAnniversaryJob() {
                notificationTemplateService.ensureDefaultTemplates();
                LocalDate today = LocalDate.now();
                List<User> activeUsers = userRepository.findByStatusOrderByTotalPointsDesc(UserStatus.ACTIVE);
                int sentCount = 0;

                for (User target : activeUsers) {
                        if (target.getDob() != null
                                        && target.getDob().getMonthValue() == today.getMonthValue()
                                        && target.getDob().getDayOfMonth() == today.getDayOfMonth()) {
                                sentCount += broadcastBirthdayOrAnniversary(target, "birthday");
                        }

                        if (target.getJoinDate() != null
                                        && target.getJoinDate().getMonthValue() == today.getMonthValue()
                                        && target.getJoinDate().getDayOfMonth() == today.getDayOfMonth()) {
                                sentCount += broadcastBirthdayOrAnniversary(target, "work anniversary");
                        }
                }

                return sentCount;
        }

        @Transactional
        public int runEventReminderJob() {
                notificationTemplateService.ensureDefaultTemplates();
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime oneHourLater = now.plusHours(1);
                int sentCount = 0;

                List<Event> events = eventRepository.findByEventDateBetween(now, oneHourLater);
                for (Event event : events) {
                        List<EventAttendee> attendees = eventAttendeeRepository.findByEventId(event.getId());
                        Set<Long> notifiedUserIds = attendees.stream()
                                        .filter(attendee -> attendee.getRsvpStatus() != RsvpStatus.NO)
                                        .map(attendee -> attendee.getUser().getId())
                                        .collect(Collectors.toSet());

                        for (EventAttendee attendee : attendees) {
                                User user = attendee.getUser();
                                if (user.getStatus() != UserStatus.ACTIVE || !notifiedUserIds.contains(user.getId())) {
                                        continue;
                                }
                                sentCount += sendEventReminderToUser(user, event.getName(), event.getEventDate(), event.getLocation(), "/events");
                                notifiedUserIds.remove(user.getId());
                        }
                }

                List<Seminar> seminars = seminarRepository.findByScheduledAtBetween(now, oneHourLater);
                if (!seminars.isEmpty()) {
                        List<User> activeUsers = userRepository.findByStatusOrderByTotalPointsDesc(UserStatus.ACTIVE);
                        for (Seminar seminar : seminars) {
                                for (User user : activeUsers) {
                                        sentCount += sendEventReminderToUser(
                                                        user,
                                                        seminar.getTitle(),
                                                        seminar.getScheduledAt(),
                                                        "seminar room",
                                                        "/seminars"
                                        );
                                }
                        }
                }

                return sentCount;
        }

        @Transactional
        public int runSurveyReminderJob() {
                notificationTemplateService.ensureDefaultTemplates();
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime oneDayLater = now.plusDays(1);
                List<Survey> dueSoonSurveys = surveyRepository.findByDeadlineBetween(now, oneDayLater);
                int sentCount = 0;

                for (Survey survey : dueSoonSurveys) {
                        List<UserSurvey> pendingAssignments = userSurveyRepository.findBySurveyIdAndCompletedFalse(survey.getId());
                        for (UserSurvey assignment : pendingAssignments) {
                                if (assignment.getUser().getStatus() != UserStatus.ACTIVE) {
                                        continue;
                                }
                                sentCount += sendSurveyNotificationToUser(assignment.getUser(), survey);
                        }
                }

                return sentCount;
        }

        private int broadcastBirthdayOrAnniversary(User celebratedUser, String occasion) {
                NotificationTemplateService.RenderedTemplate renderedTemplate = notificationTemplateService.renderTemplate(
                                NotificationTemplateService.TPL_BIRTHDAY_ANNIVERSARY,
                                Map.of(
                                                "name", celebratedUser.getFullName(),
                                                "occasion", occasion
                                ),
                                "Celebration for " + celebratedUser.getFullName(),
                                "Today is " + celebratedUser.getFullName() + "'s " + occasion + ". Let's send best wishes!"
                );

                List<User> activeUsers = userRepository.findByStatusOrderByTotalPointsDesc(UserStatus.ACTIVE);
                int sentCount = 0;
                for (User user : activeUsers) {
                        sentCount += createAndSendNotification(
                                        user,
                                        renderedTemplate.subject(),
                                        renderedTemplate.body(),
                                        NotificationType.INFO,
                                        "/members",
                                        renderedTemplate.subject(),
                                        renderedTemplate.body()
                        );
                }
                return sentCount;
        }

        private int sendEventReminderToUser(User user, String eventName, LocalDateTime eventTime, String location, String actionUrl) {
                String locationValue = (location == null || location.isBlank()) ? "TBD" : location;
                NotificationTemplateService.RenderedTemplate renderedTemplate = notificationTemplateService.renderTemplate(
                                NotificationTemplateService.TPL_EVENT_REMINDER,
                                Map.of(
                                                "eventName", eventName,
                                                "eventTime", eventTime != null ? eventTime.format(DATE_TIME_FORMATTER) : "TBD",
                                                "location", locationValue
                                ),
                                "Event starts in 1 hour: " + eventName,
                                "Reminder: " + eventName + " starts at "
                                                + (eventTime != null ? eventTime.format(DATE_TIME_FORMATTER) : "TBD")
                                                + " in " + locationValue + "."
                );

                return createAndSendNotification(
                                user,
                                renderedTemplate.subject(),
                                renderedTemplate.body(),
                                NotificationType.REMINDER,
                                actionUrl,
                                renderedTemplate.subject(),
                                renderedTemplate.body()
                );
        }

        private int sendSurveyNotificationToUser(User user, Survey survey) {
                NotificationTemplateService.RenderedTemplate renderedTemplate = notificationTemplateService.renderTemplate(
                                NotificationTemplateService.TPL_SURVEY_REMINDER,
                                Map.of(
                                                "surveyTitle", survey.getTitle(),
                                                "deadline", survey.getDeadline().format(DATE_TIME_FORMATTER)
                                ),
                                "Survey deadline is approaching: " + survey.getTitle(),
                                "Please complete survey \"" + survey.getTitle() + "\" before "
                                                + survey.getDeadline().format(DATE_TIME_FORMATTER) + "."
                );

                return createAndSendNotification(
                                user,
                                renderedTemplate.subject(),
                                renderedTemplate.body(),
                                NotificationType.REMINDER,
                                "/surveys",
                                renderedTemplate.subject(),
                                renderedTemplate.body()
                );
        }

        private int createAndSendNotification(
                        User user,
                        String title,
                        String message,
                        NotificationType type,
                        String actionUrl,
                        String emailSubject,
                        String emailBody
        ) {
                Notification notification = new Notification();
                notification.setUser(user);
                notification.setTitle(title);
                notification.setMessage(message);
                notification.setType(type);
                notification.setActionUrl(actionUrl);

                Notification saved = notificationRepository.save(notification);
                sendRealtimeToUser(saved);
                dispatchByConfiguredChannels(user, title, message, type, actionUrl, emailSubject, emailBody);
                return 1;
        }

        private void dispatchByConfiguredChannels(
                        User user,
                        String title,
                        String message,
                        NotificationType type,
                        String actionUrl,
                        String emailSubject,
                        String emailBody
        ) {
                List<NotificationChannel> channels = notificationChannelRepository.findByEnabledTrueOrderByTypeAscIdAsc();
                if (channels.isEmpty()) {
                        notificationEmailService.sendEmail(user.getEmail(), emailSubject, emailBody);
                        return;
                }

                boolean emailConfigured = false;
                for (NotificationChannel channel : channels) {
                        if (channel.getType() == NotificationChannelType.EMAIL) {
                                emailConfigured = true;
                                notificationEmailService.sendEmail(user.getEmail(), emailSubject, emailBody);
                        }
                        if (channel.getType() == NotificationChannelType.WEBHOOK) {
                                Map<String, Object> payload = new HashMap<>();
                                payload.put("userId", user.getId());
                                payload.put("username", user.getUsername());
                                payload.put("email", user.getEmail());
                                payload.put("title", title);
                                payload.put("message", message);
                                payload.put("type", type.name());
                                payload.put("actionUrl", actionUrl);
                                sendWebhook(channel.getEndpoint(), payload);
                        }
                }

                if (!emailConfigured) {
                        notificationEmailService.sendEmail(user.getEmail(), emailSubject, emailBody);
                }
        }

        private void sendWebhook(String endpoint, Map<String, Object> payload) {
                if (endpoint == null || endpoint.isBlank()) {
                        return;
                }

                try {
                        webClient.post()
                                        .uri(endpoint)
                                        .bodyValue(payload)
                                        .retrieve()
                                        .toBodilessEntity()
                                        .block();
                } catch (Exception ignored) {
                        // Best-effort webhook dispatch.
                }
        }

        private User getCurrentUser() {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
                        throw new ResourceNotFoundException("Authenticated user was not found in security context");
                }

                return userRepository.findByUsername(authentication.getName())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "User not found for username=" + authentication.getName()
                                ));
        }

        private void sendRealtimeToUser(Notification notification) {
                messagingTemplate.convertAndSendToUser(
                                notification.getUser().getUsername(),
                                "/queue/notifications",
                                toRealtimeResponse(notification)
                );
        }

        private NotificationInboxResponse toInboxResponse(Notification notification) {
                return new NotificationInboxResponse(
                                notification.getId(),
                                notification.getTitle(),
                                notification.getMessage(),
                                notification.getType().name(),
                                notification.isRead(),
                                notification.getActionUrl(),
                                notification.getCreatedAt(),
                                notification.getReadAt()
                );
        }

        private NotificationRealtimeResponse toRealtimeResponse(Notification notification) {
                return new NotificationRealtimeResponse(
                                notification.getId(),
                                notification.getTitle(),
                                notification.getMessage(),
                                notification.getType().name(),
                                notification.getActionUrl(),
                                notification.getCreatedAt()
                );
    }
}
