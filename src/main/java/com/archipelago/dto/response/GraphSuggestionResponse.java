package com.archipelago.dto.response;

import java.util.List;

public record GraphSuggestionResponse(
        MovieResponse candidateMovie,
        String category,
        double confidence,
        List<GraphSuggestionEvidenceResponse> evidence,
        boolean existingEdge
) {
}
