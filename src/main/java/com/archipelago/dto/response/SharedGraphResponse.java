package com.archipelago.dto.response;

public record SharedGraphResponse(
        String shareToken,
        String title,
        MovieConnectionsResponse graph
) {
}
