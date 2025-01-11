package com.archipelago.auth;

import com.archipelago.model.User;

public interface AuthService {
    User register(String email, String password, String username);
    User authenticate(String email, String password);
    void logout(String token);
    String refreshToken(String oldToken);
    void handleForgotPassword(String email);
    void resetPassword(String token, String newPassword);
}
