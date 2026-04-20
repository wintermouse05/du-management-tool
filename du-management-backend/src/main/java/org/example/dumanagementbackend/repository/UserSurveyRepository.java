package org.example.dumanagementbackend.repository;

import org.example.dumanagementbackend.entity.UserSurvey;
import org.example.dumanagementbackend.entity.UserSurveyId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSurveyRepository extends JpaRepository<UserSurvey, UserSurveyId> {

    List<UserSurvey> findBySurveyId(Long surveyId);
}
