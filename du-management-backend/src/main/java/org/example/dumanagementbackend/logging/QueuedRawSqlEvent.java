package org.example.dumanagementbackend.logging;

import java.time.LocalDateTime;

public record QueuedRawSqlEvent(
        LocalDateTime occurredAt,
        String sql,
        String operation,
        String actorUsername,
        String correlationId,
        String threadName
) {
}
