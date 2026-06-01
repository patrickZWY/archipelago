package com.archipelago.dto.response;

import java.time.LocalDateTime;

public record SharedGraphExportSummaryResponse(
        String shareToken,
        String shareUrl,
        String title,
        Long rootMovieId,
        String rootMovieTitle,
        LocalDateTime creationTime
) {
}
