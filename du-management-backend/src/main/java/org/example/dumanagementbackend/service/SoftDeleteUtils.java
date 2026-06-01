package org.example.dumanagementbackend.service;

import org.example.dumanagementbackend.entity.SoftDeletableEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

final class SoftDeleteUtils {

    private SoftDeleteUtils() {
    }

    static void markDeleted(SoftDeletableEntity entity) {
        entity.markDeleted(currentActor());
    }

    private static String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return "system";
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails
                && userDetails.getUsername() != null
                && !userDetails.getUsername().isBlank()) {
            return userDetails.getUsername();
        }

        if (principal instanceof String principalString
                && !principalString.isBlank()
                && !"anonymousUser".equalsIgnoreCase(principalString)) {
            return principalString;
        }

        return "system";
    }
}
