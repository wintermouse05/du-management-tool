package org.example.dumanagementbackend.logging;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.example.dumanagementbackend.entity.enums.SystemLogCategory;
import org.example.dumanagementbackend.entity.enums.SystemLogSeverity;
import org.example.dumanagementbackend.entity.enums.SystemLogStatus;
import org.example.dumanagementbackend.service.SystemLogCreateRequest;
import org.example.dumanagementbackend.service.SystemLogService;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class SystemLogRequestFilter extends OncePerRequestFilter {

    public static final String CORRELATION_HEADER = "X-Correlation-Id";

    private final SystemLogService systemLogService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/") || path.startsWith("/api/system-logs");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String previousCorrelationId = MDC.get(SystemLogContext.CORRELATION_ID_KEY);
        String correlationId = resolveCorrelationId(request);
        MDC.put(SystemLogContext.CORRELATION_ID_KEY, correlationId);
        response.setHeader(CORRELATION_HEADER, correlationId);

        long startedAt = System.nanoTime();
        Throwable failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (Throwable ex) {
            failure = ex;
            throw ex;
        } finally {
            logRequest(request, response, correlationId, startedAt, failure);
            if (previousCorrelationId == null) {
                MDC.remove(SystemLogContext.CORRELATION_ID_KEY);
            } else {
                MDC.put(SystemLogContext.CORRELATION_ID_KEY, previousCorrelationId);
            }
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String incoming = Optional.ofNullable(request.getHeader(CORRELATION_HEADER))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElse(null);
        if (incoming != null && incoming.length() <= 100 && incoming.matches("[A-Za-z0-9._:-]+")) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }

    private void logRequest(
            HttpServletRequest request,
            HttpServletResponse response,
            String correlationId,
            long startedAt,
            Throwable failure
    ) {
        int status = response.getStatus();
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        SystemLogStatus logStatus = failure != null || status >= 500
                ? SystemLogStatus.FAILED
                : SystemLogStatus.SUCCESS;
        SystemLogSeverity severity = failure != null || status >= 500
                ? SystemLogSeverity.ERROR
                : status >= 400 ? SystemLogSeverity.WARN : SystemLogSeverity.INFO;
        String path = request.getRequestURI();
        String query = SystemLogSanitizer.maskText(request.getQueryString());
        String message = request.getMethod() + " " + path + " -> " + status;

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("method", request.getMethod());
        details.put("path", path);
        details.put("query", SystemLogSanitizer.truncate(query, 1000));
        details.put("status", status);
        details.put("remoteAddress", request.getRemoteAddr());
        details.put("userAgent", request.getHeader("User-Agent"));
        details.put("contentType", request.getContentType());

        systemLogService.log(new SystemLogCreateRequest(
                SystemLogCategory.HTTP_REQUEST,
                severity,
                logStatus,
                request.getMethod(),
                path,
                SystemLogContext.getActorUsername(),
                correlationId,
                "HTTP",
                path,
                durationMs,
                message,
                details,
                failure != null ? failure.getClass().getName() : null,
                SystemLogSanitizer.stackTrace(failure)
        ));
    }
}
