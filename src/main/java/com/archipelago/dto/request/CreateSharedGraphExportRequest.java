package com.archipelago.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSharedGraphExportRequest(
        @NotNull(message = "Movie id is required") Long movieId,
        @Size(max = 255, message = "Share title must be at most 255 characters") String title
) {
}
