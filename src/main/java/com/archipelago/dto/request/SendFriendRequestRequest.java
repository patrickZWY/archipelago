package com.archipelago.dto.request;

import jakarta.validation.constraints.NotNull;

public record SendFriendRequestRequest(
        @NotNull(message = "Recipient user id is required")
        Long recipientUserId
) {
}
