package org.example.dumanagementbackend.service;

import org.example.dumanagementbackend.dto.notification.NotificationJobResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    public List<NotificationJobResponse> getSystemJobs() {
        return List.of(
                new NotificationJobResponse(
                        "CRON_BIRTHDAY_ANNIVERSARY",
                        "0 0 8 * * *",
                        "Daily birthday and join-date anniversary notification"
                ),
                new NotificationJobResponse(
                        "CRON_EVENT_REMINDER",
                        "0 0 * * * *",
                        "Remind one hour before seminar/event"
                ),
                new NotificationJobResponse(
                        "CRON_SURVEY_REMINDER",
                        "0 0 9 * * *",
                        "Remind users before survey deadline"
                )
        );
    }

    public String triggerSurveyReminder(Long surveyId) {
        return "Triggered survey reminder for surveyId=" + surveyId;
    }
}
