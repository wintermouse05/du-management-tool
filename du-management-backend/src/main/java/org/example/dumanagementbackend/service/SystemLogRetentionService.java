package org.example.dumanagementbackend.service;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SystemLogRetentionService {

    private final SystemLogService systemLogService;
    private final SystemLogSettingsService systemLogSettingsService;

    @Scheduled(cron = "${app.system-logs.retention-cron:0 30 2 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void deleteOldLogs() {
        systemLogService.deleteOlderThan(LocalDateTime.now().minusDays(systemLogSettingsService.getRetentionDays()));
    }
}
