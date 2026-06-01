package org.example.dumanagementbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.example.dumanagementbackend.entity.NotificationChannel;
import org.example.dumanagementbackend.entity.Notification;
import org.example.dumanagementbackend.entity.OrderSession;
import org.example.dumanagementbackend.entity.Survey;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.UserSurvey;
import org.example.dumanagementbackend.entity.UserSurveyId;
import org.example.dumanagementbackend.entity.enums.NotificationChannelType;
import org.example.dumanagementbackend.entity.enums.NotificationType;
import org.example.dumanagementbackend.entity.enums.OrderSessionStatus;
import org.example.dumanagementbackend.entity.enums.UserStatus;
import org.example.dumanagementbackend.repository.EventRepository;
import org.example.dumanagementbackend.repository.NotificationChannelRepository;
import org.example.dumanagementbackend.repository.NotificationRepository;
import org.example.dumanagementbackend.repository.NotificationScheduleRepository;
import org.example.dumanagementbackend.repository.OrderSessionRepository;
import org.example.dumanagementbackend.repository.SeminarRepository;
import org.example.dumanagementbackend.repository.SurveyRepository;
import org.example.dumanagementbackend.repository.UserRepository;
import org.example.dumanagementbackend.repository.UserSurveyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderSessionRepository orderSessionRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private SeminarRepository seminarRepository;

    @Mock
    private SurveyRepository surveyRepository;

    @Mock
    private UserSurveyRepository userSurveyRepository;

    @Mock
    private NotificationTemplateService notificationTemplateService;

    @Mock
    private NotificationEmailService notificationEmailService;

    @Mock
    private NotificationChannelRepository notificationChannelRepository;

    @Mock
    private NotificationScheduleRepository notificationScheduleRepository;

    @Mock
    private ChatopsNotificationService chatopsNotificationService;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void runOrderSessionCreatorReminderJob_sendsReminderToCreator() {
        OrderSession session = new OrderSession();
        session.setId(15L);
        session.setName("Lunch Team");
        session.setStatus(OrderSessionStatus.OPEN);
        session.setCreatedBy("creator");
        session.setDeadline(LocalDateTime.now().plusMinutes(5));

        User creator = new User();
        creator.setId(7L);
        creator.setUsername("creator");
        creator.setEmail("creator@example.com");

        when(orderSessionRepository.findByStatusAndDeadlineBetweenAndDeadlineReminderSentAtIsNull(
                eq(OrderSessionStatus.OPEN),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.of(session));
        when(orderSessionRepository.markDeadlineReminderSentIfNeeded(eq(15L), any(LocalDateTime.class))).thenReturn(1);
        when(userRepository.findByUsername("creator")).thenReturn(Optional.of(creator));
        when(notificationTemplateService.renderTemplate(
                eq(NotificationTemplateService.TPL_ORDER_SESSION_CREATOR_REMINDER),
                anyMap(),
                anyString(),
                anyString()
        )).thenReturn(new NotificationTemplateService.RenderedTemplate(
                "Order session deadline in 5 minutes: Lunch Team",
                "Order session \"Lunch Team\" will reach deadline at 2026-05-22 12:00."
        ));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationChannelRepository.findByEnabledTrueOrderByTypeAscIdAsc()).thenReturn(List.of());

        int sentCount = notificationService.runOrderSessionCreatorReminderJob();

        assertEquals(1, sentCount);
        verify(notificationTemplateService).ensureDefaultTemplates();

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();
        assertEquals(creator, saved.getUser());
        assertEquals(NotificationType.REMINDER, saved.getType());
        assertEquals("/orders", saved.getActionUrl());
    }

    @Test
    void runOrderSessionCreatorReminderJob_skipsSessionWhenCreatorNotFound() {
        OrderSession session = new OrderSession();
        session.setId(21L);
        session.setCreatedBy("missing-user");
        session.setDeadline(LocalDateTime.now().plusMinutes(5));

        when(orderSessionRepository.findByStatusAndDeadlineBetweenAndDeadlineReminderSentAtIsNull(
                eq(OrderSessionStatus.OPEN),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.of(session));
        when(userRepository.findByUsername("missing-user")).thenReturn(Optional.empty());

        int sentCount = notificationService.runOrderSessionCreatorReminderJob();

        assertEquals(0, sentCount);
        verify(orderSessionRepository, never()).markDeadlineReminderSentIfNeeded(eq(21L), any(LocalDateTime.class));
        verify(notificationRepository, never()).save(any(Notification.class));
        verify(notificationTemplateService, never()).renderTemplate(anyString(), anyMap(), anyString(), anyString());
    }

    @Test
    void runOrderSessionCreatorReminderJob_skipsSessionWhenReminderAlreadyClaimed() {
        OrderSession session = new OrderSession();
        session.setId(33L);
        session.setName("Already Claimed");
        session.setStatus(OrderSessionStatus.OPEN);
        session.setCreatedBy("creator");
        session.setDeadline(LocalDateTime.now().plusMinutes(5));
        User creator = new User();
        creator.setId(7L);
        creator.setUsername("creator");
        creator.setEmail("creator@example.com");

        when(orderSessionRepository.findByStatusAndDeadlineBetweenAndDeadlineReminderSentAtIsNull(
                eq(OrderSessionStatus.OPEN),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.of(session));
        when(userRepository.findByUsername("creator")).thenReturn(Optional.of(creator));
        when(orderSessionRepository.markDeadlineReminderSentIfNeeded(eq(33L), any(LocalDateTime.class))).thenReturn(0);

        int sentCount = notificationService.runOrderSessionCreatorReminderJob();

        assertEquals(0, sentCount);
        verify(notificationRepository, never()).save(any(Notification.class));
        verify(notificationTemplateService, never()).renderTemplate(anyString(), anyMap(), anyString(), anyString());
    }

    @Test
    void triggerSurveyReminder_postsSingleChatMessageForMultiplePendingUsers() {
        injectChatopsNotificationService();

        Survey survey = new Survey();
        survey.setId(42L);
        survey.setTitle("Q2 Feedback");
        survey.setDeadline(LocalDateTime.now().plusHours(4));

        User userOne = buildActiveUser(1L, "alice", "alice@example.com");
        User userTwo = buildActiveUser(2L, "bob", "bob@example.com");
        UserSurvey assignmentOne = buildPendingAssignment(survey, userOne);
        UserSurvey assignmentTwo = buildPendingAssignment(survey, userTwo);

        NotificationChannel chatChannelA = new NotificationChannel();
        chatChannelA.setType(NotificationChannelType.CHAT);
        chatChannelA.setEnabled(true);
        chatChannelA.setEndpoint("");

        NotificationChannel chatChannelB = new NotificationChannel();
        chatChannelB.setType(NotificationChannelType.CHAT);
        chatChannelB.setEnabled(true);
        chatChannelB.setEndpoint("");

        when(surveyRepository.findById(42L)).thenReturn(Optional.of(survey));
        when(userSurveyRepository.findBySurveyIdAndCompletedFalse(42L)).thenReturn(List.of(assignmentOne, assignmentTwo));
        when(notificationTemplateService.renderTemplate(
                eq(NotificationTemplateService.TPL_SURVEY_REMINDER),
                anyMap(),
                anyString(),
                anyString()
        )).thenReturn(new NotificationTemplateService.RenderedTemplate(
                "Survey deadline is approaching: Q2 Feedback",
                "Please complete survey \"Q2 Feedback\" before 2026-05-22 12:00."
        ));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationChannelRepository.findByEnabledTrueOrderByTypeAscIdAsc()).thenReturn(List.of(chatChannelA, chatChannelB));
        when(chatopsNotificationService.sendThreadReply(anyString(), any())).thenReturn("root-post-1");

        String result = notificationService.triggerSurveyReminder(42L);

        assertEquals("Triggered survey reminder for surveyId=42 to 2 user(s)", result);
        verify(notificationRepository, times(2)).save(any(Notification.class));
        verify(chatopsNotificationService, times(1)).sendThreadReply(anyString(), any());
    }

    private void injectChatopsNotificationService() {
        try {
            Field field = NotificationService.class.getDeclaredField("chatopsNotificationService");
            field.setAccessible(true);
            field.set(notificationService, chatopsNotificationService);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to inject ChatopsNotificationService into NotificationService", ex);
        }
    }

    private User buildActiveUser(Long id, String username, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private UserSurvey buildPendingAssignment(Survey survey, User user) {
        UserSurvey assignment = new UserSurvey();
        UserSurveyId id = new UserSurveyId();
        id.setSurveyId(survey.getId());
        id.setUserId(user.getId());
        assignment.setId(id);
        assignment.setSurvey(survey);
        assignment.setUser(user);
        assignment.setCompleted(false);
        return assignment;
    }
}
