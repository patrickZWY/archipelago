package com.archipelago.dto.response;

import com.archipelago.model.User;

public record PublicUserResponse(Long id, String username) {

    public static PublicUserResponse from(User user) {
        return new PublicUserResponse(user.getId(), user.getUsername());
    }
}
