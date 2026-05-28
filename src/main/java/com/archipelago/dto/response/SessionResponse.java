package com.archipelago.dto.response;

import com.archipelago.model.User;

public record SessionResponse(boolean authenticated, UserSummaryResponse user) {

    public static SessionResponse authenticated(User user) {
        return new SessionResponse(true, UserSummaryResponse.from(user));
    }

    public static SessionResponse anonymous() {
        return new SessionResponse(false, null);
    }
}
