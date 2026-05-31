package com.archipelago.service.impl;

import com.archipelago.dto.request.SendFriendRequestRequest;
import com.archipelago.dto.response.FriendProfileResponse;
import com.archipelago.dto.response.FriendRequestResponse;
import com.archipelago.dto.response.FriendRequestsResponse;
import com.archipelago.dto.response.MovieResponse;
import com.archipelago.dto.response.PublicUserResponse;
import com.archipelago.exception.IllegalStateException;
import com.archipelago.exception.ResourceNotFoundException;
import com.archipelago.mapper.FriendshipMapper;
import com.archipelago.mapper.UserMapper;
import com.archipelago.model.Friendship;
import com.archipelago.model.User;
import com.archipelago.model.enums.FriendshipStatus;
import com.archipelago.security.CurrentUserProvider;
import com.archipelago.service.FriendAuthorizationService;
import com.archipelago.service.FriendshipService;
import com.archipelago.service.GraphAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FriendshipServiceImpl implements FriendshipService {

    private static final int SEARCH_LIMIT = 15;

    private final UserMapper userMapper;
    private final FriendshipMapper friendshipMapper;
    private final CurrentUserProvider currentUserProvider;
    private final GraphAccessService graphAccessService;
    private final FriendAuthorizationService friendAuthorizationService;

    @Override
    public List<PublicUserResponse> searchUsers(String query) {
        User currentUser = currentUserProvider.getCurrentUser();
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        return userMapper.searchByUsername(query.trim(), currentUser.getId(), SEARCH_LIMIT).stream()
                .map(PublicUserResponse::from)
                .toList();
    }

    @Override
    public List<PublicUserResponse> getFriends() {
        User currentUser = currentUserProvider.getCurrentUser();
        return friendshipMapper.findAcceptedForUser(currentUser.getId()).stream()
                .map(friendship -> friendship.getRequester().getId().equals(currentUser.getId())
                        ? friendship.getRecipient()
                        : friendship.getRequester())
                .map(PublicUserResponse::from)
                .toList();
    }

    @Override
    public FriendRequestsResponse getFriendRequests() {
        User currentUser = currentUserProvider.getCurrentUser();
        Long currentUserId = currentUser.getId();
        return new FriendRequestsResponse(
                friendshipMapper.findIncomingPendingForUser(currentUserId).stream()
                        .map(friendship -> FriendRequestResponse.from(friendship, currentUserId))
                        .toList(),
                friendshipMapper.findOutgoingPendingForUser(currentUserId).stream()
                        .map(friendship -> FriendRequestResponse.from(friendship, currentUserId))
                        .toList()
        );
    }

    @Override
    public FriendRequestResponse sendFriendRequest(SendFriendRequestRequest request) {
        User currentUser = currentUserProvider.getCurrentUser();
        if (currentUser.getId().equals(request.recipientUserId())) {
            throw new IllegalStateException("You cannot send a friend request to yourself");
        }

        User recipient = userMapper.findActiveById(request.recipientUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Friendship friendship = friendshipMapper.findByUserPair(currentUser.getId(), recipient.getId()).orElse(null);
        if (friendship == null) {
            friendship = Friendship.builder()
                    .requester(currentUser)
                    .recipient(recipient)
                    .status(FriendshipStatus.PENDING)
                    .build();
            friendshipMapper.insert(friendship);
            return FriendRequestResponse.from(friendshipMapper.findById(friendship.getId()).orElseThrow(), currentUser.getId());
        }

        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
            throw new IllegalStateException("You are already friends");
        }
        if (friendship.getStatus() == FriendshipStatus.PENDING) {
            if (friendship.getRequester().getId().equals(currentUser.getId())) {
                throw new IllegalStateException("Friend request already sent");
            }
            throw new IllegalStateException("This user already sent you a friend request");
        }

        friendship.setRequester(currentUser);
        friendship.setRecipient(recipient);
        friendship.setStatus(FriendshipStatus.PENDING);
        friendshipMapper.update(friendship);
        return FriendRequestResponse.from(friendshipMapper.findById(friendship.getId()).orElseThrow(), currentUser.getId());
    }

    @Override
    public FriendRequestResponse acceptFriendRequest(Long requestId) {
        User currentUser = currentUserProvider.getCurrentUser();
        Friendship friendship = friendshipMapper.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Friend request not found"));
        if (friendship.getStatus() != FriendshipStatus.PENDING || !friendship.getRecipient().getId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException("Friend request not found");
        }
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendshipMapper.update(friendship);
        return FriendRequestResponse.from(friendshipMapper.findById(friendship.getId()).orElseThrow(), currentUser.getId());
    }

    @Override
    public FriendRequestResponse declineFriendRequest(Long requestId) {
        User currentUser = currentUserProvider.getCurrentUser();
        Friendship friendship = friendshipMapper.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Friend request not found"));
        if (friendship.getStatus() != FriendshipStatus.PENDING || !friendship.getRecipient().getId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException("Friend request not found");
        }
        friendship.setStatus(FriendshipStatus.DECLINED);
        friendshipMapper.update(friendship);
        return FriendRequestResponse.from(friendshipMapper.findById(friendship.getId()).orElseThrow(), currentUser.getId());
    }

    @Override
    public void removeFriend(Long friendUserId) {
        User currentUser = currentUserProvider.getCurrentUser();
        if (friendshipMapper.countAcceptedBetweenUsers(currentUser.getId(), friendUserId) == 0) {
            throw new ResourceNotFoundException("Friend not found");
        }
        friendshipMapper.deleteAcceptedByUserPair(currentUser.getId(), friendUserId);
    }

    @Override
    public FriendProfileResponse getFriendProfile(Long friendUserId) {
        User currentUser = currentUserProvider.getCurrentUser();
        friendAuthorizationService.assertCanBrowseFriendGraph(currentUser.getId(), friendUserId);
        User friend = userMapper.findActiveById(friendUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return new FriendProfileResponse(
                friend.getId(),
                friend.getUsername(),
                graphAccessService.getGraphMovies(friendUserId).stream().map(MovieResponse::from).toList()
        );
    }
}
