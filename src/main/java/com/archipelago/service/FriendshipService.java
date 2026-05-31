package com.archipelago.service;

import com.archipelago.dto.request.SendFriendRequestRequest;
import com.archipelago.dto.response.FriendProfileResponse;
import com.archipelago.dto.response.FriendRequestResponse;
import com.archipelago.dto.response.FriendRequestsResponse;
import com.archipelago.dto.response.PublicUserResponse;

import java.util.List;

public interface FriendshipService {
    List<PublicUserResponse> searchUsers(String query);

    List<PublicUserResponse> getFriends();

    FriendRequestsResponse getFriendRequests();

    FriendRequestResponse sendFriendRequest(SendFriendRequestRequest request);

    FriendRequestResponse acceptFriendRequest(Long requestId);

    FriendRequestResponse declineFriendRequest(Long requestId);

    void removeFriend(Long friendUserId);

    FriendProfileResponse getFriendProfile(Long friendUserId);
}
