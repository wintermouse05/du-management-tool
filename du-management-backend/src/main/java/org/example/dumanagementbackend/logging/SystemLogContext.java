package org.example.dumanagementbackend.logging;

import java.util.Optional;

import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SystemLogContext {

    public static final String CORRELATION_ID_KEY = "correlationId";

    private SystemLogContext() {
    }

    public static String getCorrelationId() {
        return MDC.get(CORRELATION_ID_KEY);
    }

    public static String getActorUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return "anonymous";
        }

        return Optional.ofNullable(authentication.getName())
                .filter(value -> !value.isBlank())
                .orElse("anonymous");
    }
}
