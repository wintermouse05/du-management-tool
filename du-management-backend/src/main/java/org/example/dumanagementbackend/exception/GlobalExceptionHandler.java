package org.example.dumanagementbackend.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.example.dumanagementbackend.validation.PasswordPolicy;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        ProblemDetail problem = ApiProblemBuilder.build(
                HttpStatus.NOT_FOUND,
                "resource-not-found",
                "Resource Not Found",
                ApiErrorSanitizer.sanitizeNotFoundDetail(ex.getMessage()),
                request,
                ApiErrorCodeResolver.resolveNotFoundCode(ex)
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ProblemDetail> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        ProblemDetail problem = ApiProblemBuilder.build(
                HttpStatus.BAD_REQUEST,
                "bad-request",
                "Bad Request",
                ApiErrorSanitizer.sanitizeBadRequestDetail(ex.getMessage()),
                request,
                ApiErrorCodeResolver.resolveBadRequestCode(ex)
        );
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        ProblemDetail problem = ApiProblemBuilder.build(
                HttpStatus.UNAUTHORIZED,
                "invalid-credentials",
                "Unauthorized",
                "Invalid username or password",
                request,
                "INVALID_CREDENTIALS"
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ProblemDetail> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        ProblemDetail problem = ApiProblemBuilder.build(
                HttpStatus.UNAUTHORIZED,
                "unauthorized",
                "Unauthorized",
                ApiErrorSanitizer.sanitizeUnauthorizedDetail(ex.getMessage()),
                request,
                ApiErrorCodeResolver.resolveUnauthorizedCode(ex)
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> Map.of(
                        "field", ApiErrorSanitizer.sanitizeFieldName(fieldError.getField()),
                        "message", ApiErrorSanitizer.sanitizeValidationMessage(fieldError.getDefaultMessage())
                ))
                .toList();

        return buildValidationProblem(errors, request, "validation-error", "VALIDATION_ERROR");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(violation -> Map.of(
                        "field", ApiErrorSanitizer.sanitizeFieldName(violation.getPropertyPath().toString()),
                        "message", ApiErrorSanitizer.sanitizeValidationMessage(violation.getMessage())
                ))
                .toList();

        return buildValidationProblem(errors, request, "constraint-violation", "CONSTRAINT_VIOLATION");
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ProblemDetail> handleBindException(BindException ex, HttpServletRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> Map.of(
                        "field", ApiErrorSanitizer.sanitizeFieldName(fieldError.getField()),
                        "message", ApiErrorSanitizer.sanitizeValidationMessage(fieldError.getDefaultMessage())
                ))
                .toList();

        return buildValidationProblem(errors, request, "validation-error", "VALIDATION_ERROR");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingRequestParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request
    ) {
        String parameterName = ApiErrorSanitizer.sanitizeFieldName(ex.getParameterName());
        ProblemDetail problem = ApiProblemBuilder.build(
                HttpStatus.BAD_REQUEST,
                "missing-request-parameter",
                "Bad Request",
                "Missing required parameter '" + parameterName + "'.",
                request,
                "MISSING_REQUEST_PARAMETER"
        );
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingRequestHeader(
            MissingRequestHeaderException ex,
            HttpServletRequest request
    ) {
        String headerName = ApiErrorSanitizer.sanitizeFieldName(ex.getHeaderName());
        ProblemDetail problem = ApiProblemBuilder.build(
                HttpStatus.BAD_REQUEST,
                "missing-request-header",
                "Bad Request",
                "Missing required header '" + headerName + "'.",
                request,
                "MISSING_REQUEST_HEADER"
        );
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        String parameterName = ApiErrorSanitizer.sanitizeFieldName(ex.getName());
        ProblemDetail problem = ApiProblemBuilder.build(
                HttpStatus.BAD_REQUEST,
                "invalid-request-parameter",
                "Bad Request",
                "Invalid value for parameter '" + parameterName + "'.",
                request,
                "INVALID_REQUEST_PARAMETER"
        );
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ProblemDetail problem = ApiProblemBuilder.build(
                HttpStatus.FORBIDDEN,
                "access-denied",
                "Forbidden",
                "You do not have permission to access this resource",
                request,
                "ACCESS_DENIED"
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        ProblemDetail problem = ApiProblemBuilder.build(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "payload-too-large",
                "Payload Too Large",
                "File size exceeds the maximum allowed limit of 10MB",
                request,
                "PAYLOAD_TOO_LARGE"
        );
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(problem);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ApiProblemBuilder.build(
                HttpStatus.METHOD_NOT_ALLOWED,
                "method-not-allowed",
                "Method Not Allowed",
                "HTTP method is not supported for this endpoint.",
                request,
                "METHOD_NOT_ALLOWED"
        );
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(problem);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request
    ) {
        String supported = ex.getSupportedMediaTypes().stream()
                .map(Object::toString)
                .collect(Collectors.joining(", "));
        String detail = supported.isBlank()
                ? "Unsupported content type for this endpoint."
                : "Unsupported content type. Supported types: " + supported;
        ProblemDetail problem = ApiProblemBuilder.build(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "unsupported-media-type",
                "Unsupported Media Type",
                detail,
                request,
                "UNSUPPORTED_MEDIA_TYPE"
        );
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(problem);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ProblemDetail> handleMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ApiProblemBuilder.build(
                HttpStatus.NOT_ACCEPTABLE,
                "not-acceptable",
                "Not Acceptable",
                "Requested response type is not supported for this endpoint.",
                request,
                "NOT_ACCEPTABLE"
        );
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(problem);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ProblemDetail> handleMultipartException(MultipartException ex, HttpServletRequest request) {
        LOGGER.warn(
                "Invalid multipart request {} {}: {}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage(),
                ex
        );

        ProblemDetail problem = ApiProblemBuilder.build(
                HttpStatus.BAD_REQUEST,
                "invalid-multipart-request",
                "Bad Request",
                "Invalid multipart request. Please verify the uploaded file and try again.",
                request,
                "INVALID_MULTIPART_REQUEST"
        );
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        String detail = resolveRequestBodyDetail(ex);

        LOGGER.warn(
                "Malformed request body {} {}: {}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage(),
                ex
        );

        ProblemDetail problem = ApiProblemBuilder.build(
                HttpStatus.BAD_REQUEST,
                "invalid-request-content",
                "Bad Request",
                detail,
                request,
                "INVALID_REQUEST_CONTENT"
        );
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        ProblemDetail problem = ApiProblemBuilder.build(
                HttpStatus.NOT_FOUND,
                "endpoint-not-found",
                "Not Found",
                "The requested endpoint was not found.",
                request,
                "ENDPOINT_NOT_FOUND"
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        String dbMessage = ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage();
        String normalized = dbMessage == null ? "" : dbMessage.toLowerCase(Locale.ROOT);

        String errorCode = "BAD_REQUEST";
        String detail = "The request violates data constraints. Please check your input and try again.";

        if (isDuplicateUsernameViolation(normalized)) {
            errorCode = "AUTH_USERNAME_EXISTS";
            detail = "Username already exists";
        } else if (isDuplicateEmailViolation(normalized)) {
            errorCode = "AUTH_EMAIL_EXISTS";
            detail = "Email already exists";
        }

        LOGGER.warn(
                "Data integrity violation {} {}: {}",
                request.getMethod(),
                request.getRequestURI(),
                dbMessage,
                ex
        );

        ProblemDetail problem = ApiProblemBuilder.build(
                HttpStatus.BAD_REQUEST,
                "data-integrity-violation",
                "Bad Request",
                detail,
                request,
                errorCode
        );
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleException(Exception ex, HttpServletRequest request) {
        String traceId = ApiProblemBuilder.resolveTraceId(request);
        LOGGER.error(
                "Unhandled exception [traceId={}] {} {}: {}",
                traceId,
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage(),
                ex
        );

        ProblemDetail problem = ApiProblemBuilder.build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-server-error",
                "Unexpected Server Error",
                "An unexpected error occurred. Please contact support with traceId: " + traceId,
                request,
                "INTERNAL_SERVER_ERROR"
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    private boolean isDuplicateUsernameViolation(String normalizedDbMessage) {
        return normalizedDbMessage.contains("users_username_key")
                || normalizedDbMessage.contains("key (username)=")
                || normalizedDbMessage.contains("username already exists");
    }

    private boolean isDuplicateEmailViolation(String normalizedDbMessage) {
        return normalizedDbMessage.contains("users_email_key")
                || normalizedDbMessage.contains("key (email)=")
                || normalizedDbMessage.contains("email already exists");
    }

    private ResponseEntity<ProblemDetail> buildValidationProblem(
            List<Map<String, String>> errors,
            HttpServletRequest request,
            String typeKey,
            String errorCode
    ) {
        String detail = errors.stream()
                .map(item -> item.get("field") + ": " + item.get("message"))
                .collect(Collectors.joining("; "));
        ProblemDetail problem = ApiProblemBuilder.build(
                HttpStatus.BAD_REQUEST,
                typeKey,
                "Validation Error",
                detail,
                request,
                errorCode
        );
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    private String resolveRequestBodyDetail(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof InvalidFormatException invalidFormatException) {
            return toReadableFieldFormatMessage(extractLastField(invalidFormatException.getPath()));
        }
        if (cause instanceof MismatchedInputException mismatchedInputException) {
            return toReadableFieldFormatMessage(extractLastField(mismatchedInputException.getPath()));
        }
        String rawMessage = cause != null && cause.getMessage() != null ? cause.getMessage() : ex.getMessage();
        if (rawMessage != null) {
            String normalized = rawMessage.toLowerCase(Locale.ROOT);
            if (normalized.contains("password") || normalized.contains("newpassword")) {
                return PasswordPolicy.MESSAGE;
            }
        }
        return "Invalid request content. Please verify input format and try again.";
    }

    private String toReadableFieldFormatMessage(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return "Invalid request content. Please verify input format and try again.";
        }
        if ("password".equals(fieldName) || "newPassword".equals(fieldName)) {
            return PasswordPolicy.MESSAGE;
        }
        return "Invalid value for field '" + fieldName + "'.";
    }

    private String extractLastField(List<JsonMappingException.Reference> path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        JsonMappingException.Reference last = path.get(path.size() - 1);
        if (last == null) {
            return null;
        }
        if (last.getFieldName() != null && !last.getFieldName().isBlank()) {
            return last.getFieldName();
        }
        if (last.getIndex() >= 0) {
            return "[" + last.getIndex() + "]";
        }
        return null;
    }
}
