package com.archipelago.service;

public interface FriendAuthorizationService {
    void assertCanBrowseFriendGraph(Long viewerUserId, Long ownerUserId);
}
