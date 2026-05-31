package com.archipelago.dto.response;

import com.archipelago.model.Friendship;
import com.archipelago.model.User;

public record FriendRequestResponse(
        Long id,
        String status,
        PublicUserResponse requester,
        PublicUserResponse recipient,
        PublicUserResponse otherUser
) {

    public static FriendRequestResponse from(Friendship friendship, Long viewerUserId) {
        User otherUser = friendship.getRequester().getId().equals(viewerUserId)
                ? friendship.getRecipient()
                : friendship.getRequester();
        return new FriendRequestResponse(
                friendship.getId(),
                friendship.getStatus().name(),
                PublicUserResponse.from(friendship.getRequester()),
                PublicUserResponse.from(friendship.getRecipient()),
                PublicUserResponse.from(otherUser)
        );
    }
}
