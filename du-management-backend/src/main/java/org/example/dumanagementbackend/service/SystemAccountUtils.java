package org.example.dumanagementbackend.service;

import org.example.dumanagementbackend.entity.User;

final class SystemAccountUtils {

    static final String ADMIN_USERNAME = "admin";

    private SystemAccountUtils() {
    }

    static boolean isAdminAccount(User user) {
        return user != null
                && user.getUsername() != null
                && ADMIN_USERNAME.equalsIgnoreCase(user.getUsername());
    }
}
