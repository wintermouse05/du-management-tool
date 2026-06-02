package org.example.dumanagementbackend.service;

import java.util.List;

import org.example.dumanagementbackend.dto.project.AvailableProjectMemberResponse;
import org.example.dumanagementbackend.dto.project.ProjectAvailabilitySummaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "chatops.enabled", havingValue = "true")
public class AvailableMembersReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(AvailableMembersReportScheduler.class);

    private final ProjectService projectService;
    private final ChatopsNotificationService chatopsNotificationService;

    @Scheduled(
            cron = "${chatops.available-members-report.cron:0 0 8 * * *}",
            zone = "${chatops.available-members-report.zone:Asia/Ho_Chi_Minh}"
    )
    public void sendDailyReport() {
        ProjectAvailabilitySummaryResponse summary = projectService.getAvailabilitySummary();
        List<AvailableProjectMemberResponse> availableMembers = projectService.getAvailableMembers();
        ChatopsNotificationService.AvailableMembersReportResult result =
                chatopsNotificationService.sendAvailableMembersReport(summary, availableMembers);
        log.info(
                "Scheduled available members report completed. sent={}, openProjects={}, availableMembers={}",
                result.sent(),
                summary.openProjectCount(),
                result.availableMemberCount()
        );
    }
}
