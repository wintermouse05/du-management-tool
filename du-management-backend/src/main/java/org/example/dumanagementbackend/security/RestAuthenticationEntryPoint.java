package org.example.dumanagementbackend.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    public static final String AUTH_ERROR_MESSAGE_ATTR = "auth_error_message";

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {
        String message = (String) request.getAttribute(AUTH_ERROR_MESSAGE_ATTR);
        if (message == null || message.isBlank()) {
            message = "Authentication is required. Please sign in again.";
        }

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(buildResponseBody(message, request.getRequestURI()));
    }

    private String buildResponseBody(String message, String path) {
        return """
                {"timestamp":"%s","status":401,"error":"Unauthorized","message":"%s","path":"%s"}
                """.formatted(
                LocalDateTime.now(),
                escapeJson(message),
                escapeJson(path)
        );
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
