package org.example.dumanagementbackend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.example.dumanagementbackend.dto.notification.NotificationInboxResponse;
import org.example.dumanagementbackend.dto.notification.NotificationRealtimeResponse;
import org.example.dumanagementbackend.dto.notification.NotificationUnreadCountResponse;
import org.example.dumanagementbackend.entity.Event;
import org.example.dumanagementbackend.entity.NotificationChannel;
import org.example.dumanagementbackend.entity.Notification;
import org.example.dumanagementbackend.entity.NotificationSchedule;
import org.example.dumanagementbackend.entity.OrderSession;
import org.example.dumanagementbackend.entity.Seminar;
import org.example.dumanagementbackend.entity.Survey;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.UserSurvey;
import org.example.dumanagementbackend.entity.enums.NotificationChannelType;
import org.example.dumanagementbackend.entity.enums.NotificationScheduleType;
import org.example.dumanagementbackend.entity.enums.NotificationType;
import org.example.dumanagementbackend.entity.enums.OrderSessionStatus;
import org.example.dumanagementbackend.entity.enums.SystemLogCategory;
import org.example.dumanagementbackend.entity.enums.SystemLogSeverity;
import org.example.dumanagementbackend.entity.enums.SystemLogStatus;
import org.example.dumanagementbackend.entity.enums.UserStatus;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.logging.SystemLogContext;
import org.example.dumanagementbackend.logging.SystemLogSanitizer;
import org.example.dumanagementbackend.repository.EventRepository;
import org.example.dumanagementbackend.repository.NotificationChannelRepository;
import org.example.dumanagementbackend.repository.NotificationRepository;
import org.example.dumanagementbackend.repository.NotificationScheduleRepository;
import org.example.dumanagementbackend.repository.OrderSessionRepository;
import org.example.dumanagementbackend.repository.SeminarRepository;
import org.example.dumanagementbackend.repository.SurveyRepository;
import org.example.dumanagementbackend.repository.UserRepository;
import org.example.dumanagementbackend.repository.UserSurveyRepository;
import org.springframework.beans.factory.annotation.Autowired;
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
        private static final long EVENT_REMINDER_DAYS_BEFORE_START = 3L;
        private static final long ORDER_SESSION_REMINDER_MINUTES_BEFORE_DEADLINE = 5L;
    private final SimpMessagingTemplate messagingTemplate;
        private final NotificationRepository notificationRepository;
        private final UserRepository userRepository;
        private final OrderSessionRepository orderSessionRepository;
        private final EventRepository eventRepository;
        private final SeminarRepository seminarRepository;
        private final SurveyRepository surveyRepository;
        private final UserSurveyRepository userSurveyRepository;
        private final NotificationTemplateService notificationTemplateService;
        private final NotificationEmailService notificationEmailService;
        private final NotificationChannelRepository notificationChannelRepository;
        private final NotificationScheduleRepository notificationScheduleRepository;
        private final WebClient webClient;

        @Autowired(required = false)
        private ChatopsNotificationService chatopsNotificationService;

        @Autowired(required = false)
        private SystemLogService systemLogService;

        public Page<NotificationInboxResponse> getMyNotifications(Pageable pageable) {
                User currentUser = getCurrentUser();
                Pageable resolvedPageable = PaginationUtils.toZeroBasedPageable(pageable);
                return notificationRepository
                                .findByUserIdOrderByCreatedAtDesc(currentUser.getId(), resolvedPageable)
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

        public void ensureSurveyExists(Long surveyId) {
                if (!surveyRepository.existsById(surveyId)) {
                        throw new ResourceNotFoundException("Survey not found with id=" + surveyId);
                }
        }

        @Transactional
        public String triggerSurveyReminder(Long surveyId) {
                Survey survey = surveyRepository.findById(surveyId)
                                .orElseThrow(() -> new ResourceNotFoundException("Survey not found with id=" + surveyId));
                List<UserSurvey> pendingAssignments = userSurveyRepository.findBySurveyIdAndCompletedFalse(surveyId);
                ChatopsThreadContext chatopsThreadContext = new ChatopsThreadContext(survey.getChatopsThreadId());

                if (pendingAssignments.isEmpty()) {
                        return "No pending users found for surveyId=" + surveyId;
                }

                int sentCount = 0;
                for (UserSurvey assignment : pendingAssignments) {
                        if (!isEligibleSurveyReminderUser(assignment.getUser())) {
                                continue;
                        }

                        sentCount += sendSurveyNotificationToUser(assignment.getUser(), survey, chatopsThreadContext);
                }

                persistSurveyThreadIfNeeded(survey, chatopsThreadContext.rootPostId());
                return "Triggered survey reminder for surveyId=" + surveyId + " to " + sentCount + " user(s)";
        }

        @Transactional
        public String triggerEventReminder(Long eventId) {
                Event event = eventRepository.findById(eventId)
                                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id=" + eventId));
                int sentCount = sendEventReminderForAudience(event, "manual");
                return "Triggered event reminder for eventId=" + eventId + " to " + sentCount + " user(s)";
        }

        @Transactional
        public int runBirthdayAnniversaryJob() {
                notificationTemplateService.ensureDefaultTemplates();
                LocalDate today = LocalDate.now();
                List<User> activeUsers = userRepository.findByStatusOrderByTotalPointsDesc(UserStatus.ACTIVE);
                ScheduleThreadContext birthdayThreadContext = loadScheduleThreadContext(NotificationScheduleType.BIRTHDAY);
                ScheduleThreadContext anniversaryThreadContext = loadScheduleThreadContext(NotificationScheduleType.ANNIVERSARY);
                int sentCount = 0;

                for (User target : activeUsers) {
                        if (target.getDob() != null
                                        && target.getDob().getMonthValue() == today.getMonthValue()
                                        && target.getDob().getDayOfMonth() == today.getDayOfMonth()) {
                                sentCount += broadcastBirthdayOrAnniversary(target, "birthday", birthdayThreadContext.threadContext());
                        }

                        if (target.getJoinDate() != null
                                        && target.getJoinDate().getMonthValue() == today.getMonthValue()
                                        && target.getJoinDate().getDayOfMonth() == today.getDayOfMonth()) {
                                sentCount += broadcastBirthdayOrAnniversary(target, "work anniversary", anniversaryThreadContext.threadContext());
                        }
                }

                persistScheduleThreadIfNeeded(birthdayThreadContext);
                persistScheduleThreadIfNeeded(anniversaryThreadContext);
                return sentCount;
        }

        @Transactional
        public int runEventReminderJob() {
                notificationTemplateService.ensureDefaultTemplates();
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime eventReminderWindowStart = now.plusDays(EVENT_REMINDER_DAYS_BEFORE_START);
                LocalDateTime eventReminderWindowEnd = eventReminderWindowStart.plusHours(1);
                LocalDateTime oneHourLater = now.plusHours(1);
                int sentCount = 0;

                List<Event> events = eventRepository.findByEventDateBetween(eventReminderWindowStart, eventReminderWindowEnd);
                for (Event event : events) {
                        sentCount += sendEventReminderForAudience(event, EVENT_REMINDER_DAYS_BEFORE_START + " days");
                }

                List<Seminar> seminars = seminarRepository.findByScheduledAtBetween(now, oneHourLater);
                if (!seminars.isEmpty()) {
                        List<User> activeUsers = userRepository.findByStatusOrderByTotalPointsDesc(UserStatus.ACTIVE);
                        for (Seminar seminar : seminars) {
                                ChatopsThreadContext chatopsThreadContext = new ChatopsThreadContext(seminar.getChatopsThreadId());
                                for (User user : activeUsers) {
                                        sentCount += sendEventReminderToUser(
                                                        user,
                                                        seminar.getTitle(),
                                                        seminar.getScheduledAt(),
                                                        "seminar room",
                                                        seminar.getDescription(),
                                                        "/seminars",
                                                        "1 hour",
                                                        chatopsThreadContext
                                        );
                                }
                                persistSeminarThreadIfNeeded(seminar, chatopsThreadContext.rootPostId());
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
                        ChatopsThreadContext chatopsThreadContext = new ChatopsThreadContext(survey.getChatopsThreadId());
                        List<UserSurvey> pendingAssignments = userSurveyRepository.findBySurveyIdAndCompletedFalse(survey.getId());
                        for (UserSurvey assignment : pendingAssignments) {
                                if (!isEligibleSurveyReminderUser(assignment.getUser())) {
                                        continue;
                                }
                                sentCount += sendSurveyNotificationToUser(assignment.getUser(), survey, chatopsThreadContext);
                        }
                        persistSurveyThreadIfNeeded(survey, chatopsThreadContext.rootPostId());
                }

                return sentCount;
        }

        @Transactional
        public int runOrderSessionCreatorReminderJob() {
                notificationTemplateService.ensureDefaultTemplates();
                LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
                LocalDateTime reminderWindowStart = now.plusMinutes(ORDER_SESSION_REMINDER_MINUTES_BEFORE_DEADLINE);
                LocalDateTime reminderWindowEnd = reminderWindowStart.plusMinutes(1).minusNanos(1);
                List<OrderSession> dueSoonSessions = orderSessionRepository.findByStatusAndDeadlineBetweenAndDeadlineReminderSentAtIsNull(
                                OrderSessionStatus.OPEN,
                                reminderWindowStart,
                                reminderWindowEnd
                );
                int sentCount = 0;

                for (OrderSession session : dueSoonSessions) {
                        sentCount += sendOrderSessionDeadlineReminderToCreator(session, now);
                }

                return sentCount;
        }

        private boolean claimOrderSessionDeadlineReminder(OrderSession session, LocalDateTime now) {
                if (session == null || session.getId() == null) {
                        return false;
                }
                return orderSessionRepository.markDeadlineReminderSentIfNeeded(session.getId(), now) > 0;
        }

        private int broadcastBirthdayOrAnniversary(
                        User celebratedUser,
                        String occasion,
                        ChatopsThreadContext chatopsThreadContext
        ) {
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
                                        renderedTemplate.body(),
                                        chatopsThreadContext
                        );
                }
                return sentCount;
        }

        private int sendEventReminderForAudience(Event event, String reminderWindow) {
                List<User> activeUsers = userRepository.findByStatusOrderByTotalPointsDesc(UserStatus.ACTIVE);
                String actionUrl = event.getId() != null ? "/events/" + event.getId() : "/events";
                ChatopsThreadContext chatopsThreadContext = new ChatopsThreadContext(event.getChatopsThreadId());
                int sentCount = 0;
                for (User user : activeUsers) {
                        sentCount += sendEventReminderToUser(
                                        user,
                                        event.getName(),
                                        event.getEventDate(),
                                        event.getLocation(),
                                        event.getDescription(),
                                        actionUrl,
                                        reminderWindow,
                                        chatopsThreadContext
                        );
                }
                persistEventThreadIfNeeded(event, chatopsThreadContext.rootPostId());
                return sentCount;
        }

        private int sendEventReminderToUser(
                        User user,
                        String eventName,
                        LocalDateTime eventTime,
                        String location,
                        String description,
                        String actionUrl,
                        String reminderWindow,
                        ChatopsThreadContext chatopsThreadContext
        ) {
                String locationValue = (location == null || location.isBlank()) ? "TBD" : location;
                String reminderWindowValue = (reminderWindow == null || reminderWindow.isBlank()) ? "upcoming" : reminderWindow;
                NotificationTemplateService.RenderedTemplate renderedTemplate = notificationTemplateService.renderTemplate(
                                NotificationTemplateService.TPL_EVENT_REMINDER,
                                Map.of(
                                                "eventName", eventName,
                                                "eventTime", eventTime != null ? eventTime.format(DATE_TIME_FORMATTER) : "TBD",
                                                "location", locationValue,
                                                "description", description != null ? description : "",
                                                "reminderWindow", reminderWindowValue
                                ),
                                "Event reminder (" + reminderWindowValue + "): " + eventName,
                                "Reminder: " + eventName + " starts at "
                                                + (eventTime != null ? eventTime.format(DATE_TIME_FORMATTER) : "TBD")
                                                + " in " + locationValue + " (" + reminderWindowValue + ")."
                );

                return createAndSendNotification(
                                user,
                                renderedTemplate.subject(),
                                renderedTemplate.body(),
                                NotificationType.REMINDER,
                                actionUrl,
                                renderedTemplate.subject(),
                                renderedTemplate.body(),
                                chatopsThreadContext
                );
        }

        private int sendSurveyNotificationToUser(User user, Survey survey, ChatopsThreadContext chatopsThreadContext) {
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
                                renderedTemplate.body(),
                                chatopsThreadContext
                );
        }

        private int sendOrderSessionDeadlineReminderToCreator(OrderSession session, LocalDateTime reminderClaimedAt) {
                if (session == null || session.getDeadline() == null) {
                        return 0;
                }
                String creatorUsername = session.getCreatedBy();
                if (creatorUsername == null || creatorUsername.isBlank()) {
                        return 0;
                }
                Optional<User> creatorOpt = userRepository.findByUsername(creatorUsername);
                if (creatorOpt.isEmpty()) {
                        return 0;
                }
                if (!claimOrderSessionDeadlineReminder(session, reminderClaimedAt)) {
                        return 0;
                }

                String sessionName = session.getName();
                if (sessionName == null || sessionName.isBlank()) {
                        sessionName = session.getId() != null ? "Session #" + session.getId() : "Order session";
                }

                NotificationTemplateService.RenderedTemplate renderedTemplate = notificationTemplateService.renderTemplate(
                                NotificationTemplateService.TPL_ORDER_SESSION_CREATOR_REMINDER,
                                Map.of(
                                                "sessionName", sessionName,
                                                "deadline", session.getDeadline().format(DATE_TIME_FORMATTER)
                                ),
                                "Order session deadline in 5 minutes: " + sessionName,
                                "Order session \"" + sessionName + "\" will reach deadline at "
                                                + session.getDeadline().format(DATE_TIME_FORMATTER)
                                                + ". Please review before closing."
                );

                return createAndSendNotification(
                                creatorOpt.get(),
                                renderedTemplate.subject(),
                                renderedTemplate.body(),
                                NotificationType.REMINDER,
                                "/orders",
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
                return createAndSendNotification(user, title, message, type, actionUrl, emailSubject, emailBody, null);
        }

        private int createAndSendNotification(
                        User user,
                        String title,
                        String message,
                        NotificationType type,
                        String actionUrl,
                        String emailSubject,
                        String emailBody,
                        ChatopsThreadContext chatopsThreadContext
        ) {
                Notification notification = new Notification();
                notification.setUser(user);
                notification.setTitle(title);
                notification.setMessage(message);
                notification.setType(type);
                notification.setActionUrl(actionUrl);

                Notification saved = notificationRepository.save(notification);
                sendRealtimeToUser(saved);
                dispatchByConfiguredChannels(user, title, message, type, actionUrl, emailSubject, emailBody, chatopsThreadContext);
                return 1;
        }

        private void dispatchByConfiguredChannels(
                        User user,
                        String title,
                        String message,
                        NotificationType type,
                        String actionUrl,
                        String emailSubject,
                        String emailBody,
                        ChatopsThreadContext chatopsThreadContext
        ) {
                List<NotificationChannel> channels = notificationChannelRepository.findByEnabledTrueOrderByTypeAscIdAsc();
                boolean chatConfigured = false;
                Set<String> dispatchedWebhookEndpoints = new HashSet<>();
                Map<String, Object> webhookPayload = null;
                for (NotificationChannel channel : channels) {
                        if (channel.getType() == NotificationChannelType.WEBHOOK) {
                                String endpoint = channel.getEndpoint() != null ? channel.getEndpoint().trim() : "";
                                if (endpoint.isBlank() || !dispatchedWebhookEndpoints.add(endpoint)) {
                                        continue;
                                }
                                if (webhookPayload == null) {
                                        webhookPayload = buildWebhookPayload(user, title, message, type, actionUrl);
                                }
                                sendWebhook(endpoint, webhookPayload);
                        }
                        if (channel.getType() == NotificationChannelType.CHAT) {
                                chatConfigured = true;
                        }
                }
                if (chatConfigured && shouldDispatchChatMessage(chatopsThreadContext, title, message, actionUrl)) {
                        sendChatMessage(title, message, actionUrl, chatopsThreadContext);
                }
                // Keep email delivery as a fallback/default path across all channel configurations.
                notificationEmailService.sendEmail(user.getEmail(), emailSubject, emailBody);
        }

        private Map<String, Object> buildWebhookPayload(
                        User user,
                        String title,
                        String message,
                        NotificationType type,
                        String actionUrl
        ) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("userId", user.getId());
                payload.put("username", user.getUsername());
                payload.put("email", user.getEmail());
                payload.put("title", title);
                payload.put("message", message);
                payload.put("type", type.name());
                payload.put("actionUrl", actionUrl);
                return payload;
        }

        private void sendChatMessage(
                        String title,
                        String message,
                        String actionUrl,
                        ChatopsThreadContext chatopsThreadContext
        ) {
                if (chatopsNotificationService == null) {
                        return;
                }
                String chatMsg = "**" + title + "**\n" + message;
                if (actionUrl != null && !actionUrl.isBlank()) {
                        chatMsg += "\n\n[Open](" + actionUrl + ")";
                }
                String postId;
                if (chatopsThreadContext == null) {
                        postId = chatopsNotificationService.sendToChannel(chatMsg);
                } else {
                        postId = chatopsNotificationService.sendThreadReply(chatMsg, chatopsThreadContext.rootPostId());
                }
                if (chatopsThreadContext != null && chatopsThreadContext.rootPostId() == null && postId != null) {
                        chatopsThreadContext.setRootPostId(postId);
                }
        }

        private boolean shouldDispatchChatMessage(
                        ChatopsThreadContext chatopsThreadContext,
                        String title,
                        String message,
                        String actionUrl
        ) {
                if (chatopsThreadContext == null) {
                        return true;
                }
                return chatopsThreadContext.markDispatched(buildChatDispatchKey(title, message, actionUrl));
        }

        private String buildChatDispatchKey(String title, String message, String actionUrl) {
                return normalizeDispatchKeyValue(title)
                                + "\u001f"
                                + normalizeDispatchKeyValue(message)
                                + "\u001f"
                                + normalizeDispatchKeyValue(actionUrl);
        }

        private String normalizeDispatchKeyValue(String value) {
                if (value == null) {
                        return "";
                }
                return value.trim();
        }

        private void sendWebhook(String endpoint, Map<String, Object> payload) {
                if (endpoint == null || endpoint.isBlank()) {
                        logMessageEvent("WEBHOOK", "NotificationService", null, SystemLogStatus.SKIPPED,
                                        SystemLogSeverity.INFO, "Webhook endpoint is blank", null);
                        return;
                }

                try {
                        webClient.post()
                                        .uri(endpoint)
                                        .bodyValue(payload)
                                        .retrieve()
                                        .toBodilessEntity()
                                        .block();
                        logMessageEvent("WEBHOOK", "NotificationService", endpoint, SystemLogStatus.SUCCESS,
                                        SystemLogSeverity.INFO, "Webhook dispatched", null);
                } catch (Exception ignored) {
                        // Best-effort webhook dispatch.
                        logMessageEvent("WEBHOOK", "NotificationService", endpoint, SystemLogStatus.FAILED,
                                        SystemLogSeverity.ERROR, "Webhook dispatch failed", ignored);
                }
        }

        private void persistEventThreadIfNeeded(Event event, String rootPostId) {
                if (event == null || event.getId() == null || rootPostId == null || rootPostId.isBlank()) {
                        return;
                }
                if (event.getChatopsThreadId() != null && !event.getChatopsThreadId().isBlank()) {
                        return;
                }
                event.setChatopsThreadId(rootPostId);
                eventRepository.save(event);
        }

        private void persistSurveyThreadIfNeeded(Survey survey, String rootPostId) {
                if (survey == null || survey.getId() == null || rootPostId == null || rootPostId.isBlank()) {
                        return;
                }
                if (survey.getChatopsThreadId() != null && !survey.getChatopsThreadId().isBlank()) {
                        return;
                }
                survey.setChatopsThreadId(rootPostId);
                surveyRepository.save(survey);
        }

        private void persistSeminarThreadIfNeeded(Seminar seminar, String rootPostId) {
                if (seminar == null || seminar.getId() == null || rootPostId == null || rootPostId.isBlank()) {
                        return;
                }
                if (seminar.getChatopsThreadId() != null && !seminar.getChatopsThreadId().isBlank()) {
                        return;
                }
                seminar.setChatopsThreadId(rootPostId);
                seminarRepository.save(seminar);
        }

        private ScheduleThreadContext loadScheduleThreadContext(NotificationScheduleType scheduleType) {
                NotificationSchedule schedule = notificationScheduleRepository.findByType(scheduleType).orElse(null);
                String rootPostId = schedule != null ? schedule.getChatopsPostId() : null;
                return new ScheduleThreadContext(schedule, new ChatopsThreadContext(rootPostId));
        }

        private void persistScheduleThreadIfNeeded(ScheduleThreadContext scheduleThreadContext) {
                if (scheduleThreadContext == null || scheduleThreadContext.schedule() == null) {
                        return;
                }
                NotificationSchedule schedule = scheduleThreadContext.schedule();
                String rootPostId = scheduleThreadContext.threadContext().rootPostId();
                if (schedule.getChatopsPostId() != null && !schedule.getChatopsPostId().isBlank()) {
                        return;
                }
                if (rootPostId == null || rootPostId.isBlank()) {
                        return;
                }
                schedule.setChatopsPostId(rootPostId);
                notificationScheduleRepository.save(schedule);
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
                logMessageEvent(
                                "WEBSOCKET_NOTIFICATION",
                                "NotificationService",
                                notification.getUser() != null ? notification.getUser().getUsername() : null,
                                SystemLogStatus.SUCCESS,
                                SystemLogSeverity.INFO,
                                "Realtime notification sent",
                                null
                );
        }

        private void logMessageEvent(
                        String action,
                        String source,
                        String targetId,
                        SystemLogStatus status,
                        SystemLogSeverity severity,
                        String message,
                        Throwable failure
        ) {
                if (systemLogService == null) {
                        return;
                }
                Map<String, Object> details = new HashMap<>();
                details.put("targetId", targetId);
                details.put("source", source);

                systemLogService.log(new SystemLogCreateRequest(
                                SystemLogCategory.MESSAGE,
                                severity,
                                status,
                                action,
                                source,
                                SystemLogContext.getActorUsername(),
                                SystemLogContext.getCorrelationId(),
                                "NotificationMessage",
                                targetId,
                                null,
                                message,
                                details,
                                failure != null ? failure.getClass().getName() : null,
                                SystemLogSanitizer.stackTrace(failure)
                ));
        }

        private boolean isEligibleSurveyReminderUser(User user) {
                if (user == null || user.getStatus() != UserStatus.ACTIVE) {
                        return false;
                }
                return !SystemAccountUtils.isAdminAccount(user);
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

        private static final class ChatopsThreadContext {

                private String rootPostId;
                private final Set<String> dispatchedMessageKeys = new HashSet<>();

                private ChatopsThreadContext(String rootPostId) {
                        this.rootPostId = rootPostId;
                }

                private String rootPostId() {
                        return rootPostId;
                }

                private void setRootPostId(String rootPostId) {
                        this.rootPostId = rootPostId;
                }

                private boolean markDispatched(String messageKey) {
                        return dispatchedMessageKeys.add(messageKey);
                }
        }

        private record ScheduleThreadContext(NotificationSchedule schedule, ChatopsThreadContext threadContext) {
        }
}
