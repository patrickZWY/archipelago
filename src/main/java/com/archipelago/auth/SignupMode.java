package com.archipelago.auth;

public enum SignupMode {
    PUBLIC,
    INVITE,
    APPROVAL;

    public static SignupMode fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return APPROVAL;
        }
        return SignupMode.valueOf(value.trim().replace('-', '_').toUpperCase());
    }
}
