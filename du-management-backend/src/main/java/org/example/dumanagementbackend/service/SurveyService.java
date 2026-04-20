package org.example.dumanagementbackend.service;

import org.example.dumanagementbackend.dto.survey.SurveyCompletionRequest;
import org.example.dumanagementbackend.dto.survey.SurveyProgressResponse;
import org.example.dumanagementbackend.dto.survey.SurveyRequest;
import org.example.dumanagementbackend.dto.survey.SurveyResponse;
import org.example.dumanagementbackend.entity.Survey;
import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.UserSurvey;
import org.example.dumanagementbackend.entity.UserSurveyId;
import org.example.dumanagementbackend.exception.ResourceNotFoundException;
import org.example.dumanagementbackend.repository.SurveyRepository;
import org.example.dumanagementbackend.repository.UserRepository;
import org.example.dumanagementbackend.repository.UserSurveyRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SurveyService {

    private final SurveyRepository surveyRepository;
    private final UserRepository userRepository;
    private final UserSurveyRepository userSurveyRepository;

    @Transactional
    public SurveyResponse create(SurveyRequest request) {
        Survey survey = new Survey();
        apply(survey, request);
        return toResponse(surveyRepository.save(survey));
    }

    public List<SurveyResponse> getAll() {
        return surveyRepository.findAll().stream().map(this::toResponse).toList();
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
    public SurveyProgressResponse markCompletion(Long surveyId, SurveyCompletionRequest request) {
        Survey survey = getEntityById(surveyId);
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

        return getProgress(surveyId);
    }

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
