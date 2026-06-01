package org.example.dumanagementbackend.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class SystemLogSanitizer {

    private static final int DEFAULT_TEXT_LIMIT = 2000;
    private static final int DETAILS_LIMIT = 8000;
    private static final int STACK_TRACE_LIMIT = 12000;
    private static final String MASK = "********";
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "authorization",
            "cookie",
            "set-cookie",
            "password",
            "token",
            "access_token",
            "refresh_token",
            "secret",
            "credential",
            "jwt",
            "chatops_token",
            "mail_app_password"
    );
    private static final Pattern TOKEN_PAIR_PATTERN = Pattern.compile(
            "(?i)(authorization|password|token|refresh[_-]?token|access[_-]?token|secret|cookie|jwt)=([^&\\s]+)"
    );

    private SystemLogSanitizer() {
    }

    public static String truncate(String value) {
        return truncate(value, DEFAULT_TEXT_LIMIT);
    }

    public static String truncateDetails(String value) {
        return truncate(value, DETAILS_LIMIT);
    }

    public static String truncateStackTrace(String value) {
        return truncate(value, STACK_TRACE_LIMIT);
    }

    public static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(maxLength - 14, 0)) + "...[truncated]";
    }

    public static String maskText(String value) {
        if (value == null) {
            return null;
        }
        return TOKEN_PAIR_PATTERN.matcher(value).replaceAll("$1=" + MASK);
    }

    public static Object sanitizeDetails(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                sanitized.put(key, isSensitiveKey(key) ? MASK : sanitizeDetails(entry.getValue()));
            }
            return sanitized;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(SystemLogSanitizer::sanitizeDetails).toList();
        }
        if (value instanceof CharSequence text) {
            return truncate(maskText(text.toString()));
        }
        return value;
    }

    public static String safeUrl(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        try {
            URI uri = URI.create(value);
            String query = uri.getRawQuery();
            if (query == null || query.isBlank()) {
                return value;
            }
            return value.substring(0, value.indexOf('?') + 1) + maskText(query);
        } catch (Exception ignored) {
            return truncate(maskText(value));
        }
    }

    public static String stackTrace(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return truncateStackTrace(writer.toString());
    }

    public static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return SENSITIVE_KEYS.stream().anyMatch(normalized::contains);
    }
}
