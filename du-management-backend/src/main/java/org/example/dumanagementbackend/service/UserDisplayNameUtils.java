package org.example.dumanagementbackend.service;

import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.enums.UserStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class UserDisplayNameUtils {

    private static final String INACTIVE_SUFFIX = " (inactive)";

    private UserDisplayNameUtils() {
    }

    public static String displayName(User user) {
        return displayName(user, isCurrentUserAdmin());
    }

    public static String displayName(User user, boolean appendInactiveSuffix) {
        if (user == null) {
            return "Unknown";
        }

        String baseName = firstNonBlank(user.getFullName(), user.getUsername(), user.getEmail(), "Unknown");
        if (appendInactiveSuffix
                && user.getStatus() == UserStatus.INACTIVE
                && !baseName.endsWith(INACTIVE_SUFFIX)) {
            return baseName + INACTIVE_SUFFIX;
        }
        return baseName;
    }

    public static boolean isCurrentUserAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
