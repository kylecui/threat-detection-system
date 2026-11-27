package com.threatdetection.gateway.auth.validation;

/** Shared password policy for platform bootstrap and managed user accounts. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 16;
    public static final String DESCRIPTION = "8-16 characters";

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        return password != null
                && password.length() >= MIN_LENGTH
                && password.length() <= MAX_LENGTH;
    }
}
