package org.example.dumanagementbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.example.dumanagementbackend.dto.gamification.LeaderboardEntryResponse;
import org.example.dumanagementbackend.dto.gamification.ManualPointRequest;
import org.example.dumanagementbackend.dto.gamification.PointHistoryResponse;
import org.example.dumanagementbackend.dto.gamification.PointRuleRequest;
import org.example.dumanagementbackend.entity.PointHistory;
import org.example.dumanagementbackend.entity.PointRule;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.enums.UserStatus;
import org.example.dumanagementbackend.exception.BadRequestException;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.PointHistoryRepository;
import org.example.dumanagementbackend.repository.PointRuleRepository;
import org.example.dumanagementbackend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class GamificationServiceTest {

    @Mock
    private PointRuleRepository pointRuleRepository;

    @Mock
    private PointHistoryRepository pointHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GamificationService gamificationService;

    @Test
    void adjustManual_usesRulePointWhenPointsChangedIsNull() {
        Long userId = 1L;
        Long ruleId = 2L;

        User user = new User();
        user.setId(userId);
        user.setFullName("Test User");
        user.setTotalPoints(100);

        PointRule rule = new PointRule();
        rule.setId(ruleId);
        rule.setActionCode("MANUAL_BONUS");
        rule.setPointValue(15);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(pointRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pointHistoryRepository.save(any(PointHistory.class))).thenAnswer(invocation -> {
            PointHistory history = invocation.getArgument(0);
            history.setId(99L);
            history.setCreatedAt(LocalDateTime.of(2026, 4, 21, 10, 0));
            return history;
        });

        ManualPointRequest request = new ManualPointRequest(userId, ruleId, null, null);

        PointHistoryResponse response = gamificationService.adjustManual(request);

        assertEquals(115, user.getTotalPoints());
        assertEquals(99L, response.id());
        assertEquals(userId, response.userId());
        assertEquals(ruleId, response.ruleId());
        assertEquals(15, response.pointsChanged());
        assertEquals("Manual point adjustment", response.reason());
        verify(userRepository).save(user);
        verify(pointHistoryRepository).save(any(PointHistory.class));
    }

    @Test
    void adjustManual_throwsBadRequestWhenRuleAndPointsMissing() {
        Long userId = 1L;
        User user = new User();
        user.setId(userId);
        user.setTotalPoints(50);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ManualPointRequest request = new ManualPointRequest(userId, null, null, "missing points");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> gamificationService.adjustManual(request));

        assertEquals("pointsChanged is required when ruleId is not provided", ex.getMessage());
        verify(pointHistoryRepository, never()).save(any(PointHistory.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void leaderboard_returnsMappedPage() {
        Pageable pageable = PageRequest.of(0, 2);

        User first = new User();
        first.setId(10L);
        first.setFullName("Alice");
        first.setTotalPoints(120);

        User second = new User();
        second.setId(11L);
        second.setFullName("Bob");
        second.setTotalPoints(90);

        Page<User> userPage = new PageImpl<>(List.of(first, second), pageable, 2);
        when(userRepository.findByStatusOrderByTotalPointsDesc(UserStatus.ACTIVE, pageable)).thenReturn(userPage);

        Page<LeaderboardEntryResponse> result = gamificationService.leaderboard(pageable);

        assertEquals(2, result.getTotalElements());
        assertEquals("Alice", result.getContent().get(0).fullName());
        assertEquals(120, result.getContent().get(0).totalPoints());
        assertEquals("Bob", result.getContent().get(1).fullName());
    }

    @Test
    void updateRule_throwsNotFoundWhenRuleMissing() {
        when(pointRuleRepository.findById(7L)).thenReturn(Optional.empty());

        PointRuleRequest request = new PointRuleRequest("ANY", 10);

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> gamificationService.updateRule(7L, request)
        );

        assertEquals("Point rule not found with id=7", ex.getMessage());
    }

    @Test
    void adjustManual_keepsNullRuleWhenRuleIdNotProvided() {
        Long userId = 5L;

        User user = new User();
        user.setId(userId);
        user.setFullName("No Rule User");
        user.setTotalPoints(10);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pointHistoryRepository.save(any(PointHistory.class))).thenAnswer(invocation -> {
            PointHistory history = invocation.getArgument(0);
            history.setId(100L);
            history.setCreatedAt(LocalDateTime.now());
            return history;
        });

        PointHistoryResponse response = gamificationService.adjustManual(
                new ManualPointRequest(userId, null, -5, "manual penalty")
        );

        assertEquals(5, user.getTotalPoints());
        assertNull(response.ruleId());
        assertNull(response.actionCode());
        assertEquals(-5, response.pointsChanged());
    }
}
