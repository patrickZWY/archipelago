package com.archipelago.service.impl;

import com.archipelago.exception.ResourceNotFoundException;
import com.archipelago.mapper.UserMapper;
import com.archipelago.service.FriendAuthorizationService;
import com.archipelago.mapper.FriendshipMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FriendAuthorizationServiceImpl implements FriendAuthorizationService {

    private final FriendshipMapper friendshipMapper;
    private final UserMapper userMapper;

    @Override
    public void assertCanBrowseFriendGraph(Long viewerUserId, Long ownerUserId) {
        userMapper.findActiveById(ownerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (viewerUserId.equals(ownerUserId)) {
            return;
        }
        if (friendshipMapper.countAcceptedBetweenUsers(viewerUserId, ownerUserId) == 0) {
            throw new AccessDeniedException("Only accepted friends may browse this graph");
        }
    }
}
