package org.example.dumanagementbackend.controller;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.example.dumanagementbackend.dto.systemlog.SystemLogDetailResponse;
import org.example.dumanagementbackend.dto.systemlog.SystemLogListResponse;
import org.example.dumanagementbackend.dto.systemlog.SystemLogSettingsResponse;
import org.example.dumanagementbackend.dto.systemlog.SystemLogSettingsUpdateRequest;
import org.example.dumanagementbackend.entity.enums.SystemLogCategory;
import org.example.dumanagementbackend.entity.enums.SystemLogSeverity;
import org.example.dumanagementbackend.entity.enums.SystemLogStatus;
import org.example.dumanagementbackend.service.SystemLogSettingsService;
import org.example.dumanagementbackend.service.SystemLogSearchCriteria;
import org.example.dumanagementbackend.service.SystemLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/system-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SystemLogController {

    private final SystemLogService systemLogService;
    private final SystemLogSettingsService systemLogSettingsService;

    @GetMapping
    public ResponseEntity<Page<SystemLogListResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) SystemLogSeverity severity,
            @RequestParam(required = false) SystemLogStatus status,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            Pageable pageable
    ) {
        SystemLogSearchCriteria criteria = new SystemLogSearchCriteria(
                q,
                parseCategories(category),
                severity,
                status,
                source,
                actor,
                correlationId,
                from,
                to
        );
        return ResponseEntity.ok(systemLogService.search(criteria, pageable));
    }

    @GetMapping("/settings")
    public ResponseEntity<SystemLogSettingsResponse> getSettings() {
        return ResponseEntity.ok(systemLogSettingsService.getSettings());
    }

    @PutMapping("/settings")
    public ResponseEntity<SystemLogSettingsResponse> updateSettings(
            @Valid @RequestBody SystemLogSettingsUpdateRequest request
    ) {
        return ResponseEntity.ok(systemLogSettingsService.updateRetentionDays(request.retentionDays()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SystemLogDetailResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(systemLogService.getById(id));
    }

    private List<SystemLogCategory> parseCategories(String category) {
        if (category == null || category.isBlank()) {
            return List.of();
        }
        return Arrays.stream(category.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(SystemLogCategory::valueOf)
                .toList();
    }
}
