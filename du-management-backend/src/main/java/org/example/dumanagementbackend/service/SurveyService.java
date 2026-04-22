package org.example.dumanagementbackend.service;

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
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SurveyService {

    private final SurveyRepository surveyRepository;
    private final UserRepository userRepository;
    private final UserSurveyRepository userSurveyRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public SurveyResponse create(SurveyRequest request) {
        Survey survey = new Survey();
        apply(survey, request);
        return toResponse(surveyRepository.save(survey));
    }

    public Page<SurveyResponse> getAll(Pageable pageable) {
        return surveyRepository.findAll(pageable).map(this::toResponse);
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
    @CacheEvict(cacheNames = "surveyProgress", key = "#surveyId", beforeInvocation = true)
    public SurveyProgressResponse assignToUser(Long surveyId, Long userId) {
        Survey survey = getEntityById(surveyId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id=" + userId));

        UserSurveyId id = new UserSurveyId();
        id.setSurveyId(surveyId);
        id.setUserId(userId);

        if (userSurveyRepository.findById(id).isEmpty()) {
            UserSurvey userSurvey = new UserSurvey();
            userSurvey.setId(id);
            userSurvey.setSurvey(survey);
            userSurvey.setUser(user);
            userSurvey.setCompleted(false);
            userSurveyRepository.save(userSurvey);
        }
        return getProgress(surveyId);
    }

    @Transactional
    @CacheEvict(cacheNames = "surveyProgress", key = "#surveyId", beforeInvocation = true)
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

    @Cacheable(cacheNames = "surveyProgress", key = "#surveyId")
    public SurveyProgressResponse getProgress(Long surveyId) {
        getEntityById(surveyId);
        List<UserSurvey> assignments = userSurveyRepository.findBySurveyId(surveyId);
        long completedCount = assignments.stream().filter(UserSurvey::isCompleted).count();
        return new SurveyProgressResponse(surveyId, assignments.size(), completedCount);
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
}
