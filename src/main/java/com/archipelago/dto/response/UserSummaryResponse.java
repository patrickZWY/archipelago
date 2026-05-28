package com.archipelago.dto.response;

import com.archipelago.model.User;

public record UserSummaryResponse(Long id, String email, String username) {

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(user.getId(), user.getEmail(), user.getUsername());
    }
}
