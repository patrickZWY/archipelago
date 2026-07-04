package com.archipelago.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthConfigurationGuardTest {

    @Test
    void rejectsHttpsFrontendWhenSessionCookieIsNotSecure() {
        AuthConfigurationGuard guard = new AuthConfigurationGuard("https://demo.example.com", false, "approval");

        assertThatThrownBy(() -> guard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Secure session cookies are required");
    }

    @Test
    void acceptsLocalHttpWithInsecureCookieForDevelopment() {
        AuthConfigurationGuard guard = new AuthConfigurationGuard("http://localhost:5173", false, "public");

        assertThatCode(() -> guard.run(null)).doesNotThrowAnyException();
    }
}
