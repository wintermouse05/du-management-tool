package org.example.dumanagementbackend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.example.dumanagementbackend.exception.ApiErrorCodeResolver;
import org.example.dumanagementbackend.exception.ApiErrorSanitizer;
import org.example.dumanagementbackend.exception.ApiProblemBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    public static final String AUTH_ERROR_MESSAGE_ATTR = "auth_error_message";
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        String errorCode = ApiErrorCodeResolver.resolveUnauthorizedCode(message);
        message = ApiErrorSanitizer.sanitizeUnauthorizedDetail(message);

        ProblemDetail problem = ApiProblemBuilder.build(
                HttpStatus.UNAUTHORIZED,
                "unauthorized",
                "Unauthorized",
                message,
                request,
                errorCode
        );

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
