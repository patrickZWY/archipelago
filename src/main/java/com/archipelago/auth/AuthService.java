package com.archipelago.auth;

import com.archipelago.dto.request.LoginRequest;
import com.archipelago.dto.request.RegisterRequest;
import com.archipelago.model.User;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {
    User register(RegisterRequest request);
    User authenticate(LoginRequest request);
    User authenticateDemoUser();
    void startSession(User user, HttpServletRequest request);
    void logout(HttpServletRequest request);
    User getSessionUser();
    void verifyAccount(String token);
    void handleForgotPassword(String email);
    void resetPassword(String token, String newPassword);
}
