package org.example.dumanagementbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.example.dumanagementbackend.dto.survey.SurveyCompletionRequest;
import org.example.dumanagementbackend.dto.survey.SurveyAssignmentUpdateRequest;
import org.example.dumanagementbackend.dto.survey.SurveyProgressResponse;
import org.example.dumanagementbackend.dto.survey.SurveyRequest;
import org.example.dumanagementbackend.entity.Role;
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
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.util.ReflectionTestUtils;

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

    @Mock
    private ChatopsNotificationService chatopsNotificationService;

    @InjectMocks
    private SurveyService surveyService;

    @BeforeEach
    void injectOptionalServices() {
        ReflectionTestUtils.setField(surveyService, "chatopsNotificationService", chatopsNotificationService);
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void create_returnsResponse() {
        SurveyRequest req = new SurveyRequest("Q1 Survey", "https://form.io/q1",
                LocalDateTime.now().plusDays(14));
        Survey saved = buildSurvey(1L, "Q1 Survey", null);
        saved.setLink(req.link());
        saved.setDeadline(req.deadline());

        when(surveyRepository.save(any(Survey.class))).thenReturn(saved);

        SurveyResponse response = surveyService.create(req);

        assertEquals(1L, response.id());
        assertEquals("Q1 Survey", response.title());
        verify(chatopsNotificationService).sendSurveyCreatedNotification(
                eq(1L),
                eq("Q1 Survey"),
                eq("https://form.io/q1"),
                eq(req.deadline())
        );
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
        User user = buildUser(1L, "Mock User");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(surveyRepository.findById(10L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> surveyService.assignToUser(10L, 1L));
    }

    @Test
    void assignToUser_throwsNotFoundWhenUserMissing() {
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

    @Test
    void replaceAssignments_replacesAssignedMembers() {
        Survey survey = buildSurvey(20L, "Replace Survey", LocalDateTime.now().plusDays(3));
        User alice = buildUser(1L, "Alice");
        User bob = buildUser(2L, "Bob");
        User carol = buildUser(3L, "Carol");

        UserSurvey aliceAssignment = new UserSurvey();
        aliceAssignment.setId(buildUserSurveyId(20L, 1L));
        aliceAssignment.setSurvey(survey);
        aliceAssignment.setUser(alice);
        aliceAssignment.setCompleted(true);

        UserSurvey bobAssignment = new UserSurvey();
        bobAssignment.setId(buildUserSurveyId(20L, 2L));
        bobAssignment.setSurvey(survey);
        bobAssignment.setUser(bob);
        bobAssignment.setCompleted(false);

        UserSurvey carolAssignment = new UserSurvey();
        carolAssignment.setId(buildUserSurveyId(20L, 3L));
        carolAssignment.setSurvey(survey);
        carolAssignment.setUser(carol);
        carolAssignment.setCompleted(false);

        when(surveyRepository.findById(20L)).thenReturn(Optional.of(survey));
        when(userRepository.findAllById(List.of(1L, 3L))).thenReturn(List.of(alice, carol));
        when(userSurveyRepository.findBySurveyId(20L))
                .thenReturn(List.of(aliceAssignment, bobAssignment))
                .thenReturn(List.of(aliceAssignment, carolAssignment));

        SurveyProgressResponse response = surveyService.replaceAssignments(20L, new SurveyAssignmentUpdateRequest(List.of(1L, 3L)));

        assertEquals(2, response.totalAssigned());
        assertEquals(1, response.completedCount());
        assertEquals("Alice", response.assignments().get(0).fullName());
        assertEquals("Carol", response.assignments().get(1).fullName());
        verify(userSurveyRepository).deleteAll(org.mockito.ArgumentMatchers.<UserSurvey>anyList());
        verify(userSurveyRepository).save(any(UserSurvey.class));
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/surveys/20"),
                any(SurveyProgressResponse.class)
        );
    }

    @Test
    void replaceAssignments_throwsNotFoundWhenAnyUserMissing() {
        Survey survey = buildSurvey(21L, "Replace Survey", LocalDateTime.now().plusDays(3));
        User alice = buildUser(1L, "Alice");

        when(surveyRepository.findById(21L)).thenReturn(Optional.of(survey));
        when(userRepository.findAllById(List.of(1L, 999L))).thenReturn(List.of(alice));

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> surveyService.replaceAssignments(21L, new SurveyAssignmentUpdateRequest(List.of(1L, 999L)))
        );
        assertEquals("User not found with id=999", ex.getMessage());
    }

    @Test
    void replaceAssignments_throwsBadRequestWhenAdminAccountIncluded() {
        Survey survey = buildSurvey(22L, "Replace Survey", LocalDateTime.now().plusDays(3));
        User admin = buildUser(100L, "Admin User", "ADMIN");
        admin.setUsername("admin");

        when(surveyRepository.findById(22L)).thenReturn(Optional.of(survey));
        when(userRepository.findAllById(List.of(100L))).thenReturn(List.of(admin));

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> surveyService.replaceAssignments(22L, new SurveyAssignmentUpdateRequest(List.of(100L)))
        );
        assertEquals("Admin account cannot be assigned to surveys. userId=100", ex.getMessage());
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

        User completedUser = buildUser(11L, "Completed User");
        User pendingUser = buildUser(12L, "Pending User");

        UserSurvey completed = new UserSurvey();
        completed.setUser(completedUser);
        completed.setCompleted(true);
        UserSurvey pending = new UserSurvey();
        pending.setUser(pendingUser);
        pending.setCompleted(false);
        when(userSurveyRepository.findBySurveyId(8L)).thenReturn(List.of(completed, pending));

        SurveyProgressResponse progress = surveyService.getProgress(8L);

        assertEquals(8L, progress.surveyId());
        assertEquals(2, progress.totalAssigned());
        assertEquals(1, progress.completedCount());
        assertEquals(2, progress.assignments().size());
        assertEquals("Completed User", progress.assignments().get(0).fullName());
        assertTrue(progress.assignments().get(0).completed());
        assertEquals("Pending User", progress.assignments().get(1).fullName());
        assertTrue(!progress.assignments().get(1).completed());
    }

    @Test
    void getProgress_excludesAdminAccountFromTotals() {
        Survey survey = buildSurvey(9L, "Progress Survey", null);
        when(surveyRepository.findById(9L)).thenReturn(Optional.of(survey));

        User completedMember = buildUser(11L, "Completed Member", "MEMBER");
        User pendingAdmin = buildUser(12L, "Pending Admin", "ADMIN");
        pendingAdmin.setUsername("admin");

        UserSurvey memberAssignment = new UserSurvey();
        memberAssignment.setUser(completedMember);
        memberAssignment.setCompleted(true);

        UserSurvey adminAssignment = new UserSurvey();
        adminAssignment.setUser(pendingAdmin);
        adminAssignment.setCompleted(false);

        when(userSurveyRepository.findBySurveyId(9L)).thenReturn(List.of(memberAssignment, adminAssignment));

        SurveyProgressResponse progress = surveyService.getProgress(9L);

        assertEquals(1, progress.totalAssigned());
        assertEquals(1, progress.completedCount());
        assertEquals(1, progress.assignments().size());
        assertEquals("Completed Member", progress.assignments().get(0).fullName());
    }

    @Test
    void getProgress_includesAdminRoleUsersThatAreNotAdminAccount() {
        Survey survey = buildSurvey(10L, "Progress Survey", null);
        when(surveyRepository.findById(10L)).thenReturn(Optional.of(survey));

        User adminRoleUser = buildUser(13L, "Team Admin", "ADMIN");
        adminRoleUser.setUsername("team-admin");

        UserSurvey adminRoleAssignment = new UserSurvey();
        adminRoleAssignment.setUser(adminRoleUser);
        adminRoleAssignment.setCompleted(false);

        when(userSurveyRepository.findBySurveyId(10L)).thenReturn(List.of(adminRoleAssignment));

        SurveyProgressResponse progress = surveyService.getProgress(10L);

        assertEquals(1, progress.totalAssigned());
        assertEquals(0, progress.completedCount());
        assertEquals(1, progress.assignments().size());
        assertEquals("Team Admin", progress.assignments().get(0).fullName());
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
        return buildUser(id, fullName, "MEMBER");
    }

    private User buildUser(Long id, String fullName, String roleName) {
        User user = new User();
        user.setId(id);
        user.setUsername(fullName.toLowerCase(java.util.Locale.ROOT).replace(" ", "-"));
        user.setFullName(fullName);
        Role role = new Role();
        role.setName(roleName);
        user.setRole(role);
        return user;
    }

    private UserSurveyId buildUserSurveyId(Long surveyId, Long userId) {
        UserSurveyId id = new UserSurveyId();
        id.setSurveyId(surveyId);
        id.setUserId(userId);
        return id;
    }
}
