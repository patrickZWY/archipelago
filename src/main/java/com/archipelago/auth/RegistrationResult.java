package com.archipelago.auth;

import com.archipelago.model.User;

public record RegistrationResult(User user, boolean created) {
}
