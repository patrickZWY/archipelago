package com.archipelago.security;

import com.archipelago.model.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.time.LocalDateTime;

@Getter
public class AuthenticatedUser implements UserDetails {

    private final Long id;
    private final String email;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final LocalDateTime authenticatedAt;
    private final Collection<? extends GrantedAuthority> authorities;

    private AuthenticatedUser(
            Long id,
            String email,
            String username,
            String password,
            boolean enabled,
            LocalDateTime authenticatedAt,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.authenticatedAt = authenticatedAt;
        this.authorities = authorities;
    }

    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getPassword(),
                user.isEnabled(),
                LocalDateTime.now(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
