package org.example.dumanagementbackend.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class ApiProblemBuilder {

    public static final String TRACE_ID_ATTR = "du_trace_id";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String TYPE_PREFIX = "https://du-management.local/problems/";

    private ApiProblemBuilder() {
    }

    public static ProblemDetail build(
            HttpStatus status,
            String typeKey,
            String title,
            String detail,
            HttpServletRequest request,
            String errorCode
    ) {
        String resolvedDetail = (detail == null || detail.isBlank())
                ? defaultDetail(status)
                : detail.trim();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, resolvedDetail);
        problem.setType(resolveType(typeKey));
        problem.setTitle((title == null || title.isBlank()) ? status.getReasonPhrase() : title.trim());
        problem.setInstance(resolveInstance(request));
        problem.setProperty("errorCode", normalizeErrorCode(errorCode));
        problem.setProperty("traceId", resolveTraceId(request));
        return problem;
    }

    public static String resolveTraceId(HttpServletRequest request) {
        if (request == null) {
            return UUID.randomUUID().toString();
        }

        Object existing = request.getAttribute(TRACE_ID_ATTR);
        if (existing instanceof String traceId && !traceId.isBlank()) {
            return traceId;
        }

        String headerTraceId = request.getHeader(REQUEST_ID_HEADER);
        String traceId = (headerTraceId != null && !headerTraceId.isBlank())
                ? headerTraceId.trim()
                : UUID.randomUUID().toString();
        request.setAttribute(TRACE_ID_ATTR, traceId);
        return traceId;
    }

    private static URI resolveType(String typeKey) {
        if (typeKey == null || typeKey.isBlank()) {
            return URI.create("about:blank");
        }
        return URI.create(TYPE_PREFIX + typeKey.trim());
    }

    private static URI resolveInstance(HttpServletRequest request) {
        if (request == null) {
            return URI.create("about:blank");
        }

        String requestUri = request.getRequestURI();
        if (requestUri == null || requestUri.isBlank()) {
            return URI.create("about:blank");
        }
        return URI.create(requestUri);
    }

    private static String normalizeErrorCode(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return "UNKNOWN_ERROR";
        }
        return errorCode.trim();
    }

    private static String defaultDetail(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "The request is invalid. Please check your input and try again.";
            case UNAUTHORIZED -> "Authentication is required. Please sign in again.";
            case FORBIDDEN -> "You do not have permission to access this resource.";
            case NOT_FOUND -> "The requested resource was not found.";
            case PAYLOAD_TOO_LARGE -> "The uploaded file exceeds the allowed size limit.";
            case UNSUPPORTED_MEDIA_TYPE -> "Unsupported content type for this endpoint.";
            default -> "Unexpected server error. Please try again later.";
        };
    }
}
