package org.example.dumanagementbackend.exception;

import java.util.regex.Pattern;

public final class ApiErrorSanitizer {

    private static final int MAX_USER_DETAIL_LENGTH = 280;
    private static final int MAX_FIELD_NAME_LENGTH = 80;
    private static final Pattern TECHNICAL_DETAIL_PATTERN = Pattern.compile(
            "(?is)(\\bselect\\b.+\\bfrom\\b|\\binsert\\b.+\\binto\\b|\\bupdate\\b.+\\bset\\b|\\bdelete\\b.+\\bfrom\\b|"
                    + "\\bstack\\s*trace\\b|\\bsql\\b|\\bsqlstate\\b|\\bjdbc\\b|\\bhibernate\\b|\\bpreparedstatement\\b|"
                    + "\\bpsqlexception\\b|\\bsqlexception\\b|\\bbadsqlgrammar\\b|\\bdata\\s+integrity\\b|"
                    + "\\bduplicate\\s+key\\b|\\bforeign\\s+key\\b|\\bviolates?\\s+.*constraint\\b|"
                    + "\\brelation\\s+\"[^\"]+\"|\\bcolumn\\s+\"[^\"]+\"|\\btable\\s+\"[^\"]+\"|"
                    + "\\bcould\\s+not\\s+(execute|extract|prepare)\\b|\\bsyntax\\s+error\\s+at\\s+or\\s+near\\b|"
                    + "\\bentitymanager\\b|\\btransaction\\b|\\bsecurity\\s+context\\b|\\brequired\\s+role\\b.+\\bmissing\\b|"
                    + "\\b[a-zA-Z0-9_.$]+Exception\\b|\\b(?:java|javax|jakarta|org|com|net|io)\\.[\\w.$]+\\b|"
                    + "\\n\\s*at\\s+[\\w.$]+\\()"
    );
    private static final Pattern REDACT_SECRET_PATTERN = Pattern.compile(
            "(?i)\\b(token|password|secret|authorization)\\b\\s*[:=]\\s*[^\\s,;]+"
    );
    private static final Pattern NEWLINE_PATTERN = Pattern.compile("[\\r\\n\\t]+");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s{2,}");

    private ApiErrorSanitizer() {
    }

    public static String sanitizeBadRequestDetail(String detail) {
        return sanitize(detail, "The request is invalid. Please check your input and try again.");
    }

    public static String sanitizeNotFoundDetail(String detail) {
        return sanitize(detail, "The requested resource was not found.");
    }

    public static String sanitizeUnauthorizedDetail(String detail) {
        return sanitize(detail, "Authentication is required. Please sign in again.");
    }

    public static String sanitizeValidationMessage(String detail) {
        return sanitize(detail, "Invalid value");
    }

    public static String sanitizeFieldName(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return "value";
        }

        String normalized = normalize(fieldName);
        if (normalized.contains(".")) {
            normalized = normalized.substring(normalized.lastIndexOf('.') + 1);
        }
        if (normalized.isBlank()
                || normalized.length() > MAX_FIELD_NAME_LENGTH
                || looksTechnical(normalized)
                || !normalized.matches("[A-Za-z0-9_\\-\\[\\]]+")) {
            return "value";
        }
        return normalized;
    }

    public static boolean containsTechnicalDetail(String detail) {
        return detail != null && looksTechnical(normalize(detail));
    }

    public static String sanitize(String detail, String fallback) {
        if (detail == null || detail.isBlank()) {
            return fallback;
        }

        String normalized = normalize(detail);
        if (normalized.isBlank() || normalized.length() > MAX_USER_DETAIL_LENGTH || looksTechnical(normalized)) {
            return fallback;
        }
        return normalized;
    }

    private static boolean looksTechnical(String detail) {
        return TECHNICAL_DETAIL_PATTERN.matcher(detail).find();
    }

    private static String normalize(String detail) {
        String sanitized = NEWLINE_PATTERN.matcher(detail).replaceAll(" ");
        sanitized = MULTI_SPACE_PATTERN.matcher(sanitized).replaceAll(" ").trim();
        sanitized = REDACT_SECRET_PATTERN.matcher(sanitized).replaceAll("$1=<redacted>");
        if (sanitized.endsWith(":")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1).trim();
        }
        return sanitized;
    }
}
