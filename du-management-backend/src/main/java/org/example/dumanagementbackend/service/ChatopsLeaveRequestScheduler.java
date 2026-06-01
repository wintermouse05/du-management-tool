package org.example.dumanagementbackend.service;

import org.example.dumanagementbackend.dto.chatops.ChatopsLeaveRequestSummaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "chatops.enabled", havingValue = "true")
public class ChatopsLeaveRequestScheduler {

    private static final Logger log = LoggerFactory.getLogger(ChatopsLeaveRequestScheduler.class);

    private final ChatopsLeaveRequestService chatopsLeaveRequestService;

    @Scheduled(
            cron = "${chatops.leave-requests.fetch-cron:0 0 9 * * MON-FRI}",
            zone = "${chatops.leave-requests.zone:Asia/Ho_Chi_Minh}"
    )
    public void refreshTodayRequests() {
        ChatopsLeaveRequestSummaryResponse summary = chatopsLeaveRequestService.refreshToday();
        log.info(
                "Scheduled ChatOps WFH/OFF fetch completed for {}. total={}, wfh={}, off={}",
                summary.date(),
                summary.total(),
                summary.wfhCount(),
                summary.offCount()
        );
    }
}
