package com.archipelago.controller;

import com.archipelago.dto.request.SendFriendRequestRequest;
import com.archipelago.dto.response.FriendProfileResponse;
import com.archipelago.dto.response.FriendRequestResponse;
import com.archipelago.dto.response.FriendRequestsResponse;
import com.archipelago.dto.response.MovieConnectionsResponse;
import com.archipelago.dto.response.MoviePathResponse;
import com.archipelago.dto.response.PublicUserResponse;
import com.archipelago.security.CurrentUserProvider;
import com.archipelago.service.FriendAuthorizationService;
import com.archipelago.service.FriendshipService;
import com.archipelago.service.GraphAccessService;
import com.archipelago.util.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendshipController {

    private final FriendshipService friendshipService;
    private final GraphAccessService graphAccessService;
    private final FriendAuthorizationService friendAuthorizationService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PublicUserResponse>>> getFriends() {
        return ResponseEntity.ok(ApiResponse.success(friendshipService.getFriends(), "Friends retrieved"));
    }

    @GetMapping("/requests")
    public ResponseEntity<ApiResponse<FriendRequestsResponse>> getFriendRequests() {
        return ResponseEntity.ok(ApiResponse.success(friendshipService.getFriendRequests(), "Friend requests retrieved"));
    }

    @PostMapping("/requests")
    public ResponseEntity<ApiResponse<FriendRequestResponse>> sendFriendRequest(
            @Valid @RequestBody SendFriendRequestRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(friendshipService.sendFriendRequest(request), "Friend request sent"));
    }

    @PostMapping("/requests/{requestId}/accept")
    public ResponseEntity<ApiResponse<FriendRequestResponse>> acceptFriendRequest(@PathVariable Long requestId) {
        return ResponseEntity.ok(ApiResponse.success(friendshipService.acceptFriendRequest(requestId), "Friend request accepted"));
    }

    @PostMapping("/requests/{requestId}/decline")
    public ResponseEntity<ApiResponse<FriendRequestResponse>> declineFriendRequest(@PathVariable Long requestId) {
        return ResponseEntity.ok(ApiResponse.success(friendshipService.declineFriendRequest(requestId), "Friend request declined"));
    }

    @DeleteMapping("/{friendUserId}")
    public ResponseEntity<ApiResponse<Void>> removeFriend(@PathVariable Long friendUserId) {
        friendshipService.removeFriend(friendUserId);
        return ResponseEntity.ok(ApiResponse.success("Friend removed"));
    }

    @GetMapping("/{friendUserId}/profile")
    public ResponseEntity<ApiResponse<FriendProfileResponse>> getFriendProfile(@PathVariable Long friendUserId) {
        return ResponseEntity.ok(ApiResponse.success(friendshipService.getFriendProfile(friendUserId), "Friend profile retrieved"));
    }

    @GetMapping("/{friendUserId}/movies/{movieId}/connections")
    public ResponseEntity<ApiResponse<MovieConnectionsResponse>> getFriendMovieConnections(
            @PathVariable Long friendUserId,
            @PathVariable Long movieId
    ) {
        Long viewerUserId = currentUserProvider.getCurrentUser().getId();
        friendAuthorizationService.assertCanBrowseFriendGraph(viewerUserId, friendUserId);
        return ResponseEntity.ok(ApiResponse.success(
                graphAccessService.getMovieGraph(friendUserId, movieId),
                "Friend movie graph retrieved"
        ));
    }

    @GetMapping("/{friendUserId}/movies/path")
    public ResponseEntity<ApiResponse<MoviePathResponse>> getFriendMoviePath(
            @PathVariable Long friendUserId,
            @RequestParam("from") Long fromMovieId,
            @RequestParam("to") Long toMovieId
    ) {
        Long viewerUserId = currentUserProvider.getCurrentUser().getId();
        friendAuthorizationService.assertCanBrowseFriendGraph(viewerUserId, friendUserId);
        return ResponseEntity.ok(ApiResponse.success(
                graphAccessService.getShortestPath(friendUserId, fromMovieId, toMovieId, "These movies are not connected in your friend's graph"),
                "Friend movie path retrieved"
        ));
    }
}
