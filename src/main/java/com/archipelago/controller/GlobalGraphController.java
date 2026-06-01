package com.archipelago.controller;

import com.archipelago.dto.response.GlobalGraphPathResponse;
import com.archipelago.dto.response.GlobalGraphResponse;
import com.archipelago.dto.response.PublicUserResponse;
import com.archipelago.security.CurrentUserProvider;
import com.archipelago.service.FriendAuthorizationService;
import com.archipelago.service.FriendshipService;
import com.archipelago.service.GraphAccessService;
import com.archipelago.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/global-graphs")
@RequiredArgsConstructor
public class GlobalGraphController {

    private final GraphAccessService graphAccessService;
    private final FriendshipService friendshipService;
    private final FriendAuthorizationService friendAuthorizationService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<GlobalGraphResponse>> getCurrentUserGlobalGraph() {
        Long currentUserId = currentUserProvider.getCurrentUser().getId();
        return ResponseEntity.ok(ApiResponse.success(
                graphAccessService.getFullGraph(currentUserId),
                "Global graph retrieved"
        ));
    }

    @GetMapping("/friends")
    public ResponseEntity<ApiResponse<List<PublicUserResponse>>> getAcceptedFriends() {
        return ResponseEntity.ok(ApiResponse.success(friendshipService.getFriends(), "Friends retrieved"));
    }

    @GetMapping("/friends/aggregate")
    public ResponseEntity<ApiResponse<GlobalGraphResponse>> getAggregateFriendGraph() {
        Long currentUserId = currentUserProvider.getCurrentUser().getId();
        return ResponseEntity.ok(ApiResponse.success(
                graphAccessService.getMergedFriendGraph(currentUserId),
                "Aggregate friend graph retrieved"
        ));
    }

    @GetMapping("/friends/{friendUserId}")
    public ResponseEntity<ApiResponse<GlobalGraphResponse>> getFriendGlobalGraph(@PathVariable Long friendUserId) {
        Long viewerUserId = currentUserProvider.getCurrentUser().getId();
        friendAuthorizationService.assertCanBrowseFriendGraph(viewerUserId, friendUserId);
        return ResponseEntity.ok(ApiResponse.success(
                graphAccessService.getFullGraph(friendUserId),
                "Friend global graph retrieved"
        ));
    }

    @GetMapping("/path")
    public ResponseEntity<ApiResponse<GlobalGraphPathResponse>> getGlobalGraphPath(
            @RequestParam("scope") String scope,
            @RequestParam("from") Long fromMovieId,
            @RequestParam("to") Long toMovieId,
            @RequestParam(name = "friendUserId", required = false) Long friendUserId
    ) {
        Long currentUserId = currentUserProvider.getCurrentUser().getId();
        return switch (scope) {
            case "me" -> ResponseEntity.ok(ApiResponse.success(
                    graphAccessService.getFullGraphShortestPath(currentUserId, fromMovieId, toMovieId, "These movies are not connected in your global graph"),
                    "Global graph path retrieved"
            ));
            case "friend" -> {
                if (friendUserId == null) {
                    throw new com.archipelago.exception.IllegalStateException("Choose a friend graph before explaining a path");
                }
                friendAuthorizationService.assertCanBrowseFriendGraph(currentUserId, friendUserId);
                yield ResponseEntity.ok(ApiResponse.success(
                        graphAccessService.getFullGraphShortestPath(friendUserId, fromMovieId, toMovieId, "These movies are not connected in this friend graph"),
                        "Friend global graph path retrieved"
                ));
            }
            case "all-friends" -> ResponseEntity.ok(ApiResponse.success(
                    graphAccessService.getMergedFriendGraphShortestPath(currentUserId, fromMovieId, toMovieId, "These movies are not connected in the merged friend graph"),
                    "Aggregate friend graph path retrieved"
            ));
            default -> throw new com.archipelago.exception.IllegalStateException("Unsupported global graph scope");
        };
    }
}
