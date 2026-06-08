package org.example.dumanagementbackend.exception;

import java.util.Locale;

public final class ApiErrorCodeResolver {

    private ApiErrorCodeResolver() {
    }

    public static String resolveBadRequestCode(BadRequestException ex) {
        if (ex == null) {
            return "BAD_REQUEST";
        }
        if (hasText(ex.getErrorCode())) {
            return ex.getErrorCode().trim().toUpperCase(Locale.ROOT);
        }

        String message = normalize(ex.getMessage());
        if (contains(message, "username already exists")) {
            return "AUTH_USERNAME_EXISTS";
        }
        if (contains(message, "email already exists")) {
            return "AUTH_EMAIL_EXISTS";
        }
        if (contains(message, "invalid or expired reset token")
                || contains(message, "reset token has expired")) {
            return "PASSWORD_RESET_TOKEN_INVALID_OR_EXPIRED";
        }
        if (contains(message, "reset token has already been used")) {
            return "PASSWORD_RESET_TOKEN_ALREADY_USED";
        }
        if (contains(message, "deadline has passed") && contains(message, "survey")) {
            return "SURVEY_DEADLINE_PASSED";
        }
        if (contains(message, "cannot rsvp to an event that has already occurred")) {
            return "EVENT_RSVP_CLOSED";
        }
        if (contains(message, "session is past deadline")) {
            return "ORDER_SESSION_PAST_DEADLINE";
        }
        if (contains(message, "new deadline must be in the future")) {
            return "ORDER_SESSION_INVALID_DEADLINE";
        }
        if (contains(message, "session is not open")) {
            return "ORDER_SESSION_NOT_OPEN";
        }
        if (contains(message, "selected menu item does not belong to this order session")) {
            return "ORDER_ITEM_NOT_IN_SESSION";
        }
        if (contains(message, "userids must contain valid user ids")) {
            return "INVALID_USER_IDS";
        }
        if (contains(message, "some userids do not exist")) {
            return "USER_IDS_NOT_FOUND";
        }
        if (contains(message, "all prize slots have already been assigned")) {
            return "LUCKY_DRAW_PRIZE_FULL";
        }
        if (contains(message, "already won a prize")) {
            return "LUCKY_DRAW_USER_ALREADY_WON";
        }
        if (contains(message, "no participants configured")) {
            return "LUCKY_DRAW_NO_PARTICIPANTS";
        }
        if (contains(message, "no eligible participants left")) {
            return "LUCKY_DRAW_NO_ELIGIBLE_PARTICIPANTS";
        }
        if (contains(message, "file is required")) {
            return "FILE_REQUIRED";
        }
        if (contains(message, "only .csv or .xlsx files are supported")) {
            return "FILE_FORMAT_UNSUPPORTED";
        }
        if (contains(message, "unable to read import file")) {
            return "FILE_IMPORT_READ_FAILED";
        }
        if (contains(message, "channelurl is required")) {
            return "CHATOPS_CHANNEL_URL_REQUIRED";
        }
        if (contains(message, "token is required")) {
            return "CHATOPS_TOKEN_REQUIRED";
        }
        if (contains(message, "invalid channel url format")
                || contains(message, "channelurl must be a valid absolute url")
                || contains(message, "channelurl must contain")) {
            return "CHATOPS_CHANNEL_URL_INVALID";
        }
        if (contains(message, "unable to resolve team") || contains(message, "unable to resolve channel")) {
            return "CHATOPS_CHANNEL_RESOLVE_FAILED";
        }
        return "BAD_REQUEST";
    }

    public static String resolveNotFoundCode(ResourceNotFoundException ex) {
        if (ex == null) {
            return "RESOURCE_NOT_FOUND";
        }
        if (hasText(ex.getErrorCode())) {
            return ex.getErrorCode().trim().toUpperCase(Locale.ROOT);
        }

        String message = normalize(ex.getMessage());
        if (contains(message, "user not found")) {
            return "USER_NOT_FOUND";
        }
        if (contains(message, "event not found")) {
            return "EVENT_NOT_FOUND";
        }
        if (contains(message, "survey not found")) {
            return "SURVEY_NOT_FOUND";
        }
        if (contains(message, "order session not found")) {
            return "ORDER_SESSION_NOT_FOUND";
        }
        if (contains(message, "order not found")) {
            return "ORDER_NOT_FOUND";
        }
        if (contains(message, "menu item not found")) {
            return "MENU_ITEM_NOT_FOUND";
        }
        if (contains(message, "restaurant not found")) {
            return "RESTAURANT_NOT_FOUND";
        }
        if (contains(message, "group not found")) {
            return "GROUP_NOT_FOUND";
        }
        if (contains(message, "role not found")) {
            return "ROLE_NOT_FOUND";
        }
        if (contains(message, "seminar not found")) {
            return "SEMINAR_NOT_FOUND";
        }
        if (contains(message, "materials file is unavailable")) {
            return "SEMINAR_MATERIALS_UNAVAILABLE";
        }
        if (contains(message, "no materials found")) {
            return "SEMINAR_MATERIALS_NOT_FOUND";
        }
        if (contains(message, "lucky draw session not found")) {
            return "LUCKY_DRAW_SESSION_NOT_FOUND";
        }
        if (contains(message, "lucky draw prize not found")) {
            return "LUCKY_DRAW_PRIZE_NOT_FOUND";
        }
        if (contains(message, "bookmark not found")) {
            return "BOOKMARK_NOT_FOUND";
        }
        if (contains(message, "notification template not found")) {
            return "NOTIFICATION_TEMPLATE_NOT_FOUND";
        }
        if (contains(message, "notification job not found")) {
            return "NOTIFICATION_JOB_NOT_FOUND";
        }
        if (contains(message, "notification channel not found")) {
            return "NOTIFICATION_CHANNEL_NOT_FOUND";
        }
        return "RESOURCE_NOT_FOUND";
    }

    public static String resolveUnauthorizedCode(UnauthorizedException ex) {
        if (ex == null) {
            return "UNAUTHORIZED";
        }
        if (hasText(ex.getErrorCode())) {
            return ex.getErrorCode().trim().toUpperCase(Locale.ROOT);
        }
        return resolveUnauthorizedCode(ex.getMessage());
    }

    public static String resolveUnauthorizedCode(String message) {
        String normalized = normalize(message);
        if (contains(normalized, "something went wrong with this account")) {
            return "ACCOUNT_UNAVAILABLE";
        }
        if (contains(normalized, "refresh token has expired")) {
            return "REFRESH_TOKEN_EXPIRED";
        }
        if (contains(normalized, "refresh token is invalid")) {
            return "REFRESH_TOKEN_INVALID";
        }
        if (contains(normalized, "missing refresh token")) {
            return "REFRESH_TOKEN_MISSING";
        }
        if (contains(normalized, "access token has expired")) {
            return "ACCESS_TOKEN_EXPIRED";
        }
        if (contains(normalized, "access token is invalid")) {
            return "ACCESS_TOKEN_INVALID";
        }
        return "UNAUTHORIZED";
    }

    private static String normalize(String input) {
        return input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean contains(String value, String fragment) {
        return value.contains(fragment);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
