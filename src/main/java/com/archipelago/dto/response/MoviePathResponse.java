package com.archipelago.dto.response;

import java.util.List;

public record MoviePathResponse(
        MovieResponse fromMovie,
        MovieResponse toMovie,
        List<MovieResponse> movies,
        List<ConnectionResponse> connections
) {
}
