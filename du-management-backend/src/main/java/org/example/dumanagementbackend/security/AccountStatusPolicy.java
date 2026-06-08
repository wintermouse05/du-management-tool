package org.example.dumanagementbackend.security;

import org.example.dumanagementbackend.entity.User;
import org.example.dumanagementbackend.entity.enums.UserStatus;

public final class AccountStatusPolicy {

    public static final String ACCOUNT_UNAVAILABLE_CODE = "ACCOUNT_UNAVAILABLE";
    public static final String ACCOUNT_UNAVAILABLE_MESSAGE =
            "Something went wrong with this account. Please contact an administrator.";

    private AccountStatusPolicy() {
    }

    public static boolean isActive(User user) {
        return user != null && !user.isDeleted() && user.getStatus() == UserStatus.ACTIVE;
    }
}
