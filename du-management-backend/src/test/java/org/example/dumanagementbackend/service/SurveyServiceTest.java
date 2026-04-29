package org.example.dumanagementbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.example.dumanagementbackend.dto.survey.SurveyCompletionRequest;
import org.example.dumanagementbackend.dto.survey.SurveyProgressResponse;
import org.example.dumanagementbackend.dto.survey.SurveyRequest;
import org.example.dumanagementbackend.dto.survey.SurveyResponse;
import org.example.dumanagementbackend.entity.Survey;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.UserSurvey;
import org.example.dumanagementbackend.entity.UserSurveyId;
import org.example.dumanagementbackend.exception.BadRequestException;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.SurveyRepository;
import org.example.dumanagementbackend.repository.UserRepository;
import org.example.dumanagementbackend.repository.UserSurveyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class SurveyServiceTest {

    @Mock
    private SurveyRepository surveyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSurveyRepository userSurveyRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private SurveyService surveyService;

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void create_returnsResponse() {
        SurveyRequest req = new SurveyRequest("Q1 Survey", "https://form.io/q1",
                LocalDateTime.now().plusDays(14));
        Survey saved = buildSurvey(1L, "Q1 Survey", null);

        when(surveyRepository.save(any(Survey.class))).thenReturn(saved);

        SurveyResponse response = surveyService.create(req);

        assertEquals(1L, response.id());
        assertEquals("Q1 Survey", response.title());
    }

    // ── getAll ───────────────────────────────────────────────────────────────

    @Test
    void getAll_returnsMappedPage() {
        Pageable pageable = PageRequest.of(0, 5);
        Survey s1 = buildSurvey(1L, "Survey A", null);
        Survey s2 = buildSurvey(2L, "Survey B", null);
        Page<Survey> page = new PageImpl<>(List.of(s1, s2), pageable, 2);

        when(surveyRepository.findAll(pageable)).thenReturn(page);

        Page<SurveyResponse> result = surveyService.getAll(pageable);

        assertEquals(2, result.getTotalElements());
        assertEquals("Survey A", result.getContent().get(0).title());
    }

    // ── getById ──────────────────────────────────────────────────────────────

    @Test
    void getById_throwsNotFoundWhenSurveyMissing() {
        when(surveyRepository.findById(99L)).thenReturn(Optional.empty());
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> surveyService.getById(99L));
        assertEquals("Survey not found with id=99", ex.getMessage());
    }

    @Test
    void getById_returnsSurveyWhenFound() {
        Survey survey = buildSurvey(5L, "My Survey", null);
        when(surveyRepository.findById(5L)).thenReturn(Optional.of(survey));

        SurveyResponse response = surveyService.getById(5L);
        assertEquals(5L, response.id());
        assertEquals("My Survey", response.title());
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    void update_updatesFieldsAndReturnsResponse() {
        Survey existing = buildSurvey(3L, "Old Title", null);
        SurveyRequest req = new SurveyRequest("New Title", "https://form.io/new",
                LocalDateTime.now().plusDays(7));

        when(surveyRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(surveyRepository.save(any(Survey.class))).thenAnswer(inv -> inv.getArgument(0));

        SurveyResponse response = surveyService.update(3L, req);

        assertEquals("New Title", response.title());
        assertEquals("https://form.io/new", response.link());
    }

    // ── assignToUser ─────────────────────────────────────────────────────────

    @Test
    void assignToUser_throwsNotFoundWhenSurveyMissing() {
        when(surveyRepository.findById(10L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> surveyService.assignToUser(10L, 1L));
    }

    @Test
    void assignToUser_throwsNotFoundWhenUserMissing() {
        Survey survey = buildSurvey(10L, "Test", null);
        when(surveyRepository.findById(10L)).thenReturn(Optional.of(survey));
        when(userRepository.findById(55L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> surveyService.assignToUser(10L, 55L));
    }

    @Test
    void assignToUser_skipsCreateWhenAlreadyAssigned() {
        Survey survey = buildSurvey(10L, "Test", null);
        User user = buildUser(5L, "Alice");

        UserSurveyId id = new UserSurveyId();
        id.setSurveyId(10L);
        id.setUserId(5L);

        UserSurvey existing = new UserSurvey();
        existing.setId(id);
        existing.setSurvey(survey);
        existing.setUser(user);
        existing.setCompleted(false);

        when(surveyRepository.findById(10L)).thenReturn(Optional.of(survey));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userSurveyRepository.findById(any(UserSurveyId.class))).thenReturn(Optional.of(existing));
        when(userSurveyRepository.findBySurveyId(10L)).thenReturn(List.of(existing));

        surveyService.assignToUser(10L, 5L);

        verify(userSurveyRepository, never()).save(any(UserSurvey.class));
    }

    @Test
    void assignToUser_savesNewAssignmentWhenNotYetAssigned() {
        Survey survey = buildSurvey(10L, "Test", null);
        User user = buildUser(5L, "Alice");

        when(surveyRepository.findById(10L)).thenReturn(Optional.of(survey));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userSurveyRepository.findById(any(UserSurveyId.class))).thenReturn(Optional.empty());
        when(userSurveyRepository.findBySurveyId(10L)).thenReturn(List.of());

        surveyService.assignToUser(10L, 5L);

        verify(userSurveyRepository).save(any(UserSurvey.class));
    }

    // ── markCompletion ────────────────────────────────────────────────────────

    @Test
    void markCompletion_throwsBadRequestWhenDeadlinePassed() {
        Survey survey = buildSurvey(7L, "Expired", LocalDateTime.now().minusDays(1));
        when(surveyRepository.findById(7L)).thenReturn(Optional.of(survey));

        SurveyCompletionRequest req = new SurveyCompletionRequest(1L, true);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> surveyService.markCompletion(7L, req));
        assertEquals("Cannot complete survey because the deadline has passed.", ex.getMessage());
    }

    @Test
    void markCompletion_throwsNotFoundWhenUserMissing() {
        Survey survey = buildSurvey(7L, "Active", LocalDateTime.now().plusDays(5));
        when(surveyRepository.findById(7L)).thenReturn(Optional.of(survey));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        SurveyCompletionRequest req = new SurveyCompletionRequest(99L, true);
        assertThrows(ResourceNotFoundException.class,
                () -> surveyService.markCompletion(7L, req));
    }

    @Test
    void markCompletion_setsCompletedAndBroadcasts() {
        Survey survey = buildSurvey(7L, "Active", LocalDateTime.now().plusDays(5));
        User user = buildUser(3L, "Dave");

        UserSurveyId id = new UserSurveyId();
        id.setSurveyId(7L);
        id.setUserId(3L);

        when(surveyRepository.findById(7L)).thenReturn(Optional.of(survey));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(userSurveyRepository.findById(any(UserSurveyId.class))).thenReturn(Optional.empty());
        when(userSurveyRepository.findBySurveyId(7L)).thenReturn(List.of());

        SurveyCompletionRequest req = new SurveyCompletionRequest(3L, true);
        SurveyProgressResponse response = surveyService.markCompletion(7L, req);

        verify(userSurveyRepository).save(any(UserSurvey.class));
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/surveys/7"),
                any(SurveyProgressResponse.class)
        );
    }

    // ── getProgress ───────────────────────────────────────────────────────────

    @Test
    void getProgress_calculatesCorrectly() {
        Survey survey = buildSurvey(8L, "Progress Survey", null);
        when(surveyRepository.findById(8L)).thenReturn(Optional.of(survey));

        UserSurvey completed = new UserSurvey();
        completed.setCompleted(true);
        UserSurvey pending = new UserSurvey();
        pending.setCompleted(false);
        when(userSurveyRepository.findBySurveyId(8L)).thenReturn(List.of(completed, pending));

        SurveyProgressResponse progress = surveyService.getProgress(8L);

        assertEquals(8L, progress.surveyId());
        assertEquals(2, progress.totalAssigned());
        assertEquals(1, progress.completedCount());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Survey buildSurvey(Long id, String title, LocalDateTime deadline) {
        Survey s = new Survey();
        s.setId(id);
        s.setTitle(title);
        s.setLink("https://form.io/test");
        s.setDeadline(deadline);
        return s;
    }

    private User buildUser(Long id, String fullName) {
        User user = new User();
        user.setId(id);
        user.setFullName(fullName);
        return user;
    }
}
