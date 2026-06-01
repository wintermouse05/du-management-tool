package org.example.dumanagementbackend.validation;

public final class PasswordPolicy {

    public static final String REGEX = "^(?=\\S{8,128}$)(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[\\p{Punct}]).*$";
    public static final String FORMAT_MESSAGE = "Password does not match the required format";
    public static final String MESSAGE =
            "Password must be 8-128 characters and include uppercase, lowercase, number, and special character.";

    private PasswordPolicy() {
    }
}
