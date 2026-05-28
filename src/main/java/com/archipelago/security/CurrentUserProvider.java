package com.archipelago.security;

import com.archipelago.exception.UserNotFoundException;
import com.archipelago.mapper.UserMapper;
import com.archipelago.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentUserProvider {

    private final UserMapper userMapper;

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new UserNotFoundException("No authenticated user in session");
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof AuthenticatedUser authenticatedUser)) {
            throw new UserNotFoundException("Unexpected session principal");
        }
        return userMapper.findActiveById(authenticatedUser.getId())
                .orElseThrow(() -> new UserNotFoundException("Authenticated user no longer exists"));
    }
}
