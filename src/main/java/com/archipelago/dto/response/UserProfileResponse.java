package com.archipelago.dto.response;

import com.archipelago.model.User;

public record UserProfileResponse(Long id, String username, String email, boolean enabled, boolean verified, String accountStatus) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.isEnabled(),
                user.isVerified(),
                user.getAccountStatus().name()
        );
    }
}
