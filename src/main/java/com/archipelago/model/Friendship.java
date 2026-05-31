package com.archipelago.model;

import com.archipelago.model.enums.FriendshipStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Friendship {
    private Long id;
    private User requester;
    private User recipient;
    private FriendshipStatus status;
    private LocalDateTime creationTime;
    private LocalDateTime updateTime;
}
