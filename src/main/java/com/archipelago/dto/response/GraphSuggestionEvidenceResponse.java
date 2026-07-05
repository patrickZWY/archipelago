package com.archipelago.dto.response;

import java.util.List;

public record GraphSuggestionEvidenceResponse(
        String type,
        String label,
        List<String> values
) {
}
