package org.example.dumanagementbackend.service;

import org.example.dumanagementbackend.dto.systemlog.SystemLogSettingsResponse;
import org.example.dumanagementbackend.entity.SystemSetting;
import org.example.dumanagementbackend.exception.BadRequestException;
import org.example.dumanagementbackend.repository.SystemSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SystemLogSettingsService {

    public static final int MIN_RETENTION_DAYS = 1;
    public static final int MAX_RETENTION_DAYS = 3650;

    private static final String RETENTION_DAYS_KEY = "system.logs.retention-days";
    private static final String RETENTION_DAYS_DESCRIPTION = "Number of days system logs are retained before cleanup";

    private final SystemSettingRepository systemSettingRepository;

    @Value("${app.system-logs.retention-days:90}")
    private int defaultRetentionDays;

    public SystemLogSettingsResponse getSettings() {
        return response(getRetentionDays());
    }

    public int getRetentionDays() {
        return systemSettingRepository.findBySettingKey(RETENTION_DAYS_KEY)
                .map(SystemSetting::getSettingValue)
                .map(this::parseRetentionDays)
                .orElseGet(this::defaultRetentionDays);
    }

    @Transactional
    public SystemLogSettingsResponse updateRetentionDays(Integer retentionDays) {
        int validatedRetentionDays = validateRetentionDays(retentionDays);
        SystemSetting setting = systemSettingRepository.findBySettingKey(RETENTION_DAYS_KEY)
                .orElseGet(SystemSetting::new);

        setting.setSettingKey(RETENTION_DAYS_KEY);
        setting.setSettingValue(String.valueOf(validatedRetentionDays));
        setting.setDescription(RETENTION_DAYS_DESCRIPTION);
        systemSettingRepository.save(setting);

        return response(validatedRetentionDays);
    }

    private SystemLogSettingsResponse response(int retentionDays) {
        return new SystemLogSettingsResponse(
                retentionDays,
                defaultRetentionDays(),
                MIN_RETENTION_DAYS,
                MAX_RETENTION_DAYS
        );
    }

    private int parseRetentionDays(String value) {
        try {
            return validateRetentionDays(Integer.valueOf(value));
        } catch (RuntimeException ignored) {
            return defaultRetentionDays();
        }
    }

    private int defaultRetentionDays() {
        return Math.min(Math.max(defaultRetentionDays, MIN_RETENTION_DAYS), MAX_RETENTION_DAYS);
    }

    private int validateRetentionDays(Integer retentionDays) {
        if (retentionDays == null) {
            throw new BadRequestException("retentionDays is required");
        }
        if (retentionDays < MIN_RETENTION_DAYS || retentionDays > MAX_RETENTION_DAYS) {
            throw new BadRequestException("retentionDays must be between 1 and 3650");
        }
        return retentionDays;
    }
}
