package org.example.dumanagementbackend.logging;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import org.example.dumanagementbackend.entity.enums.SystemLogSeverity;

public class SystemLogbackAppender extends AppenderBase<ILoggingEvent> {

    private static final int MAX_QUEUE_SIZE = 5000;
    private static final LinkedBlockingDeque<QueuedBackendLogEvent> QUEUE = new LinkedBlockingDeque<>(MAX_QUEUE_SIZE);

    public static int drainTo(java.util.Collection<QueuedBackendLogEvent> target, int maxElements) {
        return QUEUE.drainTo(target, maxElements);
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (event == null || event.getLevel().isGreaterOrEqual(Level.INFO) == false) {
            return;
        }
        if (event.getLoggerName() != null
                && event.getLoggerName().startsWith("org.example.dumanagementbackend.logging.SystemLogbackAppender")) {
            return;
        }

        IThrowableProxy throwableProxy = event.getThrowableProxy();
        Map<String, String> mdc = event.getMDCPropertyMap();
        QueuedBackendLogEvent queuedEvent = new QueuedBackendLogEvent(
                LocalDateTime.ofInstant(Instant.ofEpochMilli(event.getTimeStamp()), ZoneId.systemDefault()),
                toSeverity(event.getLevel()),
                event.getLoggerName(),
                event.getThreadName(),
                SystemLogSanitizer.truncate(SystemLogSanitizer.maskText(event.getFormattedMessage())),
                mdc != null ? mdc.get(SystemLogContext.CORRELATION_ID_KEY) : null,
                throwableProxy != null ? throwableProxy.getClassName() : null,
                throwableProxy != null ? SystemLogSanitizer.truncateStackTrace(ThrowableProxyUtil.asString(throwableProxy)) : null,
                mdc
        );

        if (!QUEUE.offer(queuedEvent)) {
            QUEUE.poll();
            QUEUE.offer(queuedEvent);
        }
    }

    private SystemLogSeverity toSeverity(Level level) {
        if (level.isGreaterOrEqual(Level.ERROR)) {
            return SystemLogSeverity.ERROR;
        }
        if (level.isGreaterOrEqual(Level.WARN)) {
            return SystemLogSeverity.WARN;
        }
        return SystemLogSeverity.INFO;
    }
}
