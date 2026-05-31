package com.archipelago.dto.response;

import java.util.List;

public record FriendRequestsResponse(
        List<FriendRequestResponse> incoming,
        List<FriendRequestResponse> outgoing
) {
}
