package com.archipelago.dto.response;

import java.util.List;

public record FriendProfileResponse(
        Long id,
        String username,
        List<MovieResponse> movies
) {
}
