package com.archipelago.dto.response;

import java.util.List;

public record MovieConnectionsResponse(MovieResponse movie, List<ConnectionResponse> connections) {
}
