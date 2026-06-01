package org.example.dumanagementbackend.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.example.dumanagementbackend.entity.enums.SystemLogCategory;
import org.example.dumanagementbackend.entity.enums.SystemLogSeverity;
import org.example.dumanagementbackend.entity.enums.SystemLogStatus;
import org.example.dumanagementbackend.logging.QueuedRawSqlEvent;
import org.example.dumanagementbackend.logging.RawSqlStatementInspector;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RawSqlLogFlushService {

    private static final int MAX_BATCH_SIZE = 250;

    private final SystemLogService systemLogService;

    @Scheduled(fixedDelayString = "${app.system-logs.raw-sql-flush-ms:2000}")
    public void flushQueuedRawSqlEvents() {
        List<QueuedRawSqlEvent> events = new ArrayList<>();
        RawSqlStatementInspector.drainTo(events, MAX_BATCH_SIZE);
        if (events.isEmpty()) {
            return;
        }

        systemLogService.logAll(events.stream()
                .map(this::toRequest)
                .toList());
    }

    private SystemLogCreateRequest toRequest(QueuedRawSqlEvent event) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("sql", event.sql());
        details.put("operation", event.operation());
        details.put("threadName", event.threadName());
        details.put("occurredAt", event.occurredAt());

        return new SystemLogCreateRequest(
                SystemLogCategory.DATABASE,
                SystemLogSeverity.INFO,
                SystemLogStatus.SUCCESS,
                event.operation(),
                "Hibernate SQL",
                event.actorUsername(),
                event.correlationId(),
                "SQL",
                null,
                null,
                event.sql(),
                details,
                null,
                null
        );
    }
}
