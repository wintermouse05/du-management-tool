package org.example.dumanagementbackend.service;

import org.example.dumanagementbackend.dto.survey.SurveyCompletionRequest;
import org.example.dumanagementbackend.dto.survey.SurveyAssignmentUpdateRequest;
import org.example.dumanagementbackend.dto.survey.SurveyAssignmentStatusResponse;
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
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SurveyService {

    private final SurveyRepository surveyRepository;
    private final UserRepository userRepository;
    private final UserSurveyRepository userSurveyRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired(required = false)
    private ChatopsNotificationService chatopsNotificationService;

    @Transactional
    public SurveyResponse create(SurveyRequest request) {
        Survey survey = new Survey();
        apply(survey, request);
        Survey saved = surveyRepository.save(survey);
        triggerSurveyCreatedNotification(saved);
        return toResponse(saved);
    }

    public Page<SurveyResponse> getAll(Pageable pageable) {
        Pageable resolvedPageable = PaginationUtils.toZeroBasedPageable(pageable);
        return surveyRepository.findAll(resolvedPageable).map(this::toResponse);
    }

    public SurveyResponse getById(Long id) {
        return toResponse(getEntityById(id));
    }

    @Transactional
    public SurveyResponse update(Long id, SurveyRequest request) {
        Survey survey = getEntityById(id);
        apply(survey, request);
        return toResponse(surveyRepository.save(survey));
    }

    @Transactional
    @CacheEvict(cacheNames = "surveyProgress", allEntries = true, beforeInvocation = true)
    public SurveyProgressResponse assignToUser(Long surveyId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id=" + userId));
        if (isAdminAccount(user)) {
            throw new BadRequestException("Admin account cannot be assigned to surveys. userId=" + userId);
        }
        assignToUserInternal(surveyId, user);
        return getProgress(surveyId);
    }

    @Transactional
    @CacheEvict(cacheNames = "surveyProgress", allEntries = true, beforeInvocation = true)
    public SurveyProgressResponse replaceAssignments(Long surveyId, SurveyAssignmentUpdateRequest request) {
        Survey survey = getEntityById(surveyId);

        List<Long> normalizedUserIds = (request == null || request.userIds() == null)
                ? List.of()
                : request.userIds().stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

        for (Long userId : normalizedUserIds) {
            if (userId <= 0) {
                throw new BadRequestException("userIds must contain positive values only");
            }
        }

        List<User> users = normalizedUserIds.isEmpty() ? List.of() : userRepository.findAllById(normalizedUserIds);
        if (users.size() != normalizedUserIds.size()) {
            Set<Long> foundUserIds = users.stream().map(User::getId).collect(java.util.stream.Collectors.toSet());
            Long missingUserId = normalizedUserIds.stream().filter(id -> !foundUserIds.contains(id)).findFirst().orElse(null);
            throw new ResourceNotFoundException("User not found with id=" + missingUserId);
        }
        users.stream()
                .filter(this::isAdminAccount)
                .findFirst()
                .ifPresent(adminUser -> {
                    throw new BadRequestException(
                            "Admin account cannot be assigned to surveys. userId=" + adminUser.getId()
                    );
                });

        List<UserSurvey> existingAssignments = userSurveyRepository.findBySurveyId(surveyId);
        Set<Long> targetUserIds = new HashSet<>(normalizedUserIds);

        List<UserSurvey> assignmentsToRemove = existingAssignments.stream()
                .filter(assignment -> assignment.getUser() != null && !targetUserIds.contains(assignment.getUser().getId()))
                .toList();
        if (!assignmentsToRemove.isEmpty()) {
            userSurveyRepository.deleteAll(assignmentsToRemove);
        }

        Set<Long> existingUserIds = existingAssignments.stream()
                .map(assignment -> assignment.getUser() != null ? assignment.getUser().getId() : null)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        for (User user : users) {
            if (existingUserIds.contains(user.getId())) {
                continue;
            }
            UserSurveyId id = new UserSurveyId();
            id.setSurveyId(surveyId);
            id.setUserId(user.getId());

            UserSurvey userSurvey = new UserSurvey();
            userSurvey.setId(id);
            userSurvey.setSurvey(survey);
            userSurvey.setUser(user);
            userSurvey.setCompleted(false);
            userSurveyRepository.save(userSurvey);
        }

        SurveyProgressResponse progress = getProgress(surveyId);
        messagingTemplate.convertAndSend("/topic/surveys/" + surveyId, progress);
        return progress;
    }

    private void assignToUserInternal(Long surveyId, User user) {
        Survey survey = getEntityById(surveyId);
        UserSurveyId id = new UserSurveyId();
        id.setSurveyId(surveyId);
        id.setUserId(user.getId());

        if (userSurveyRepository.findById(id).isEmpty()) {
            UserSurvey userSurvey = new UserSurvey();
            userSurvey.setId(id);
            userSurvey.setSurvey(survey);
            userSurvey.setUser(user);
            userSurvey.setCompleted(false);
            userSurveyRepository.save(userSurvey);
        }
    }

    @Transactional
    @CacheEvict(cacheNames = "surveyProgress", allEntries = true, beforeInvocation = true)
    public SurveyProgressResponse markCompletion(Long surveyId, SurveyCompletionRequest request) {
        Survey survey = getEntityById(surveyId);
        
        if (survey.getDeadline() != null && LocalDateTime.now().isAfter(survey.getDeadline())) {
            throw new BadRequestException("Cannot complete survey because the deadline has passed.");
        }
        
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id=" + request.userId()));

        UserSurveyId id = new UserSurveyId();
        id.setSurveyId(surveyId);
        id.setUserId(request.userId());

        UserSurvey userSurvey = userSurveyRepository.findById(id).orElseGet(UserSurvey::new);
        userSurvey.setId(id);
        userSurvey.setSurvey(survey);
        userSurvey.setUser(user);
        userSurvey.setCompleted(request.completed());
        userSurvey.setCompletedAt(request.completed() ? LocalDateTime.now() : null);
        userSurveyRepository.save(userSurvey);

        SurveyProgressResponse progress = getProgress(surveyId);
        
        // Broadcast progress update
        messagingTemplate.convertAndSend("/topic/surveys/" + surveyId, progress);
        
        return progress;
    }

    @Cacheable(cacheNames = "surveyProgress", key = "{#surveyId,T(org.example.dumanagementbackend.service.UserDisplayNameUtils).isCurrentUserAdmin()}")
    public SurveyProgressResponse getProgress(Long surveyId) {
        getEntityById(surveyId);
        List<UserSurvey> assignments = userSurveyRepository.findBySurveyId(surveyId);
        List<SurveyAssignmentStatusResponse> assignmentStatuses = assignments.stream()
                .filter(assignment -> assignment.getUser() != null)
                .filter(assignment -> !isAdminAccount(assignment.getUser()))
                .map(assignment -> new SurveyAssignmentStatusResponse(
                        assignment.getUser().getId(),
                        UserDisplayNameUtils.displayName(assignment.getUser()),
                        assignment.isCompleted()
                ))
                .sorted(Comparator.comparing(
                        SurveyAssignmentStatusResponse::fullName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                ))
                .toList();
        long completedCount = assignmentStatuses.stream().filter(SurveyAssignmentStatusResponse::completed).count();
        return new SurveyProgressResponse(surveyId, assignmentStatuses.size(), completedCount, assignmentStatuses);
    }

    public Survey getEntityById(Long id) {
        return surveyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Survey not found with id=" + id));
    }

    private void apply(Survey survey, SurveyRequest request) {
        survey.setTitle(request.title());
        survey.setLink(request.link());
        survey.setDeadline(request.deadline());
    }

    private SurveyResponse toResponse(Survey survey) {
        return new SurveyResponse(survey.getId(), survey.getTitle(), survey.getLink(), survey.getDeadline());
    }

    private void triggerSurveyCreatedNotification(Survey survey) {
        ChatopsNotificationService notifier = chatopsNotificationService;
        if (notifier == null || survey == null) {
            return;
        }

        Long surveyId = survey.getId();
        String title = survey.getTitle();
        String link = survey.getLink();
        LocalDateTime deadline = survey.getDeadline();
        dispatchAfterCommit(
                "Survey created ChatOps notification",
                () -> notifier.sendSurveyCreatedNotification(surveyId, title, link, deadline)
        );
    }

    private void dispatchAfterCommit(String taskName, Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    CompletableFuture.runAsync(() -> runBestEffort(taskName, task));
                }
            });
            return;
        }

        runBestEffort(taskName, task);
    }

    private void runBestEffort(String taskName, Runnable task) {
        try {
            task.run();
        } catch (Exception ex) {
            log.warn("{} failed: {}", taskName, ex.getMessage());
        }
    }

    private boolean isAdminAccount(User user) {
        return SystemAccountUtils.isAdminAccount(user);
    }
}
