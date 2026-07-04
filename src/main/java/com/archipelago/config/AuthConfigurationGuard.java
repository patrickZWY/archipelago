package com.archipelago.config;

import com.archipelago.auth.SignupMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AuthConfigurationGuard implements ApplicationRunner {
    private final String frontendBaseUrl;
    private final boolean sessionCookieSecure;
    private final String signupMode;

    public AuthConfigurationGuard(
            @Value("${app.frontend-base-url}") String frontendBaseUrl,
            @Value("${server.servlet.session.cookie.secure:false}") boolean sessionCookieSecure,
            @Value("${app.auth.signup-mode:approval}") String signupMode
    ) {
        this.frontendBaseUrl = frontendBaseUrl;
        this.sessionCookieSecure = sessionCookieSecure;
        this.signupMode = signupMode;
    }

    @Override
    public void run(ApplicationArguments args) {
        SignupMode.fromConfig(signupMode);
        if (frontendBaseUrl.startsWith("https://") && !sessionCookieSecure) {
            throw new IllegalStateException("Secure session cookies are required when app.frontend-base-url uses HTTPS");
        }
    }
}
