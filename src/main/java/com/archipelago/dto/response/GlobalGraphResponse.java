package com.archipelago.dto.response;

import java.util.List;

public record GlobalGraphResponse(
        List<MovieResponse> movies,
        List<GlobalGraphConnectionResponse> connections
) {
}
