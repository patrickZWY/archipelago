package com.archipelago.dto.response;

public record SharedGraphExportResponse(
        String shareToken,
        String shareUrl,
        String title,
        MovieConnectionsResponse graph
) {
}
