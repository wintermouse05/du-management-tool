package org.example.dumanagementbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.Data;

@Embeddable
@Data
public class UserSurveyId implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "survey_id")
    private Long surveyId;
}
