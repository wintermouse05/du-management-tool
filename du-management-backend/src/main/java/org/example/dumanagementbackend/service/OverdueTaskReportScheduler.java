package org.example.dumanagementbackend.service;

import java.util.List;

import org.example.dumanagementbackend.dto.project.OverdueProjectTaskResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "chatops.enabled", havingValue = "true")
public class OverdueTaskReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(OverdueTaskReportScheduler.class);

    private final ProjectService projectService;
    private final ChatopsNotificationService chatopsNotificationService;

    @Scheduled(
            cron = "${chatops.overdue-tasks-report.cron:0 45 16 * * MON-FRI}",
            zone = "${chatops.overdue-tasks-report.zone:Asia/Ho_Chi_Minh}"
    )
    public void sendWeekdayReport() {
        List<OverdueProjectTaskResponse> overdueTasks = projectService.getOverdueTasks();
        ChatopsNotificationService.OverdueTaskReportResult result =
                chatopsNotificationService.sendOverdueTaskReport(overdueTasks);
        log.info(
                "Scheduled overdue tasks report completed. sent={}, overdueTasks={}",
                result.sent(),
                result.overdueTaskCount()
        );
    }
}
