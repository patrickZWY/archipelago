package com.archipelago.auth;

import com.archipelago.dto.request.LoginRequest;
import com.archipelago.dto.request.RegisterRequest;
import com.archipelago.model.User;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {
    RegistrationResult register(RegisterRequest request, HttpServletRequest httpServletRequest);
    User authenticate(LoginRequest request, HttpServletRequest httpServletRequest);
    User authenticateDemoUser(HttpServletRequest httpServletRequest);
    void startSession(User user, HttpServletRequest request);
    void logout(HttpServletRequest request);
    User getSessionUser();
    void verifyAccount(String token, HttpServletRequest httpServletRequest);
    void handleForgotPassword(String email, HttpServletRequest httpServletRequest);
    void resendVerification(String email, HttpServletRequest httpServletRequest);
    void resetPassword(String token, String newPassword, HttpServletRequest httpServletRequest);
}
