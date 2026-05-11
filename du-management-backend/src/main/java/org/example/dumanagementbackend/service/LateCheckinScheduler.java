package org.example.dumanagementbackend.service;

import java.time.LocalTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "chatops.enabled", havingValue = "true")
public class LateCheckinScheduler {

    private static final Logger log = LoggerFactory.getLogger(LateCheckinScheduler.class);

    private final LateRecordService lateRecordService;
    private final ChatopsService chatopsService;

    @Scheduled(cron = "0 05 10 * * MON-FRI", zone = "Asia/Ho_Chi_Minh")
    public void scheduledFetch() {
        log.info("Running scheduled late check-in fetch...");
        int saved = lateRecordService.fetchLateCheckinsFromChat(chatopsService.getChannelId(), LocalTime.of(10, 0));
        log.info("Scheduled late check-in fetch completed. {} records saved.", saved);
    }
}
