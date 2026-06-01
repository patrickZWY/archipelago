package com.archipelago.dto.response;

import java.util.List;

public record GlobalGraphPathResponse(
        MovieResponse fromMovie,
        MovieResponse toMovie,
        List<MovieResponse> movies,
        List<GlobalGraphConnectionResponse> connections
) {
}
