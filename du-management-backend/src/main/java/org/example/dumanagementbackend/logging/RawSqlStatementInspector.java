package org.example.dumanagementbackend.logging;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingDeque;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RawSqlStatementInspector implements StatementInspector {

    private static final int MAX_QUEUE_SIZE = 5000;
    private static final LinkedBlockingDeque<QueuedRawSqlEvent> QUEUE = new LinkedBlockingDeque<>(MAX_QUEUE_SIZE);

    private final boolean captureRawSql;

    public RawSqlStatementInspector(@Value("${app.system-logs.capture-raw-sql:true}") boolean captureRawSql) {
        this.captureRawSql = captureRawSql;
    }

    public static int drainTo(Collection<QueuedRawSqlEvent> target, int maxElements) {
        return QUEUE.drainTo(target, maxElements);
    }

    @Override
    public String inspect(String sql) {
        if (!captureRawSql || sql == null || sql.isBlank() || isSystemLogSql(sql)) {
            return sql;
        }

        QueuedRawSqlEvent event = new QueuedRawSqlEvent(
                LocalDateTime.now(),
                SystemLogSanitizer.truncateDetails(sql),
                resolveOperation(sql),
                SystemLogContext.getActorUsername(),
                SystemLogContext.getCorrelationId(),
                Thread.currentThread().getName()
        );

        if (!QUEUE.offer(event)) {
            QUEUE.poll();
            QUEUE.offer(event);
        }
        return sql;
    }

    private boolean isSystemLogSql(String sql) {
        return sql.toLowerCase(Locale.ROOT).contains("system_log_entries");
    }

    private String resolveOperation(String sql) {
        String trimmed = sql.stripLeading().toUpperCase(Locale.ROOT);
        int firstSpace = trimmed.indexOf(' ');
        String firstToken = firstSpace > 0 ? trimmed.substring(0, firstSpace) : trimmed;
        return switch (firstToken) {
            case "SELECT", "INSERT", "UPDATE", "DELETE", "ALTER", "CREATE", "DROP" -> "SQL_" + firstToken;
            default -> "SQL";
        };
    }
}
