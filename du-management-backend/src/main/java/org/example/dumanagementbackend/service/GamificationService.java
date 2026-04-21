package org.example.dumanagementbackend.service;

import org.example.dumanagementbackend.dto.gamification.LeaderboardEntryResponse;
import org.example.dumanagementbackend.dto.gamification.ManualPointRequest;
import org.example.dumanagementbackend.dto.gamification.PointHistoryResponse;
import org.example.dumanagementbackend.dto.gamification.PointRuleRequest;
import org.example.dumanagementbackend.dto.gamification.PointRuleResponse;
import org.example.dumanagementbackend.entity.PointHistory;
import org.example.dumanagementbackend.entity.PointRule;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.enums.UserStatus;
import org.example.dumanagementbackend.exception.BadRequestException;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.PointHistoryRepository;
import org.example.dumanagementbackend.repository.PointRuleRepository;
import org.example.dumanagementbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GamificationService {

    private final PointRuleRepository pointRuleRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public PointRuleResponse createRule(PointRuleRequest request) {
        PointRule rule = new PointRule();
        rule.setActionCode(request.actionCode());
        rule.setPointValue(request.pointValue());
        return toRuleResponse(pointRuleRepository.save(rule));
    }

    public Page<PointRuleResponse> getRules(Pageable pageable) {
        return pointRuleRepository.findAll(pageable).map(this::toRuleResponse);
    }

    @Transactional
    public PointRuleResponse updateRule(Long id, PointRuleRequest request) {
        PointRule rule = pointRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Point rule not found with id=" + id));
        rule.setActionCode(request.actionCode());
        rule.setPointValue(request.pointValue());
        return toRuleResponse(pointRuleRepository.save(rule));
    }

    @Transactional
    public PointHistoryResponse adjustManual(ManualPointRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id=" + request.userId()));

        PointRule rule = null;
        Integer points = request.pointsChanged();
        if (request.ruleId() != null) {
            rule = pointRuleRepository.findById(request.ruleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Point rule not found with id=" + request.ruleId()));
            if (points == null) {
                points = rule.getPointValue();
            }
        }
        if (points == null) {
            throw new BadRequestException("pointsChanged is required when ruleId is not provided");
        }

        PointHistory history = new PointHistory();
        history.setUser(user);
        history.setRule(rule);
        history.setPointsChanged(points);
        history.setReason(request.reason() != null ? request.reason() : "Manual point adjustment");

        PointHistoryResponse response = toHistoryResponse(pointHistoryRepository.save(history));
        userRepository.incrementTotalPoints(user.getId(), points);
        
        // Broadcast leaderboard update
        messagingTemplate.convertAndSend("/topic/leaderboard", "UPDATE");
        
        return response;
    }

    public Page<PointHistoryResponse> getUserHistory(Long userId, Pageable pageable) {
        return pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable).map(this::toHistoryResponse);
    }

    public Page<LeaderboardEntryResponse> leaderboard(Pageable pageable) {
        return userRepository.findByStatusOrderByTotalPointsDesc(UserStatus.ACTIVE, pageable)
                .map(user -> new LeaderboardEntryResponse(user.getId(), user.getFullName(), user.getTotalPoints()));
    }

    @Transactional
    public void applyActionPoints(Long userId, String actionCode, String reason) {
        PointRule rule = pointRuleRepository.findByActionCode(actionCode).orElse(null);
        if (rule == null) {
            return;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id=" + userId));

        PointHistory history = new PointHistory();
        history.setUser(user);
        history.setRule(rule);
        history.setPointsChanged(rule.getPointValue());
        history.setReason(reason != null && !reason.isBlank() ? reason : "Auto point adjustment: " + actionCode);
        pointHistoryRepository.save(history);
        userRepository.incrementTotalPoints(user.getId(), rule.getPointValue());

        messagingTemplate.convertAndSend("/topic/leaderboard", "UPDATE");
    }

    private PointRuleResponse toRuleResponse(PointRule rule) {
        return new PointRuleResponse(rule.getId(), rule.getActionCode(), rule.getPointValue());
    }

    private PointHistoryResponse toHistoryResponse(PointHistory history) {
        return new PointHistoryResponse(
                history.getId(),
                history.getUser().getId(),
                history.getUser().getFullName(),
                history.getRule() != null ? history.getRule().getId() : null,
                history.getRule() != null ? history.getRule().getActionCode() : null,
                history.getPointsChanged(),
                history.getReason(),
                history.getCreatedAt()
        );
    }
}
