package com.archipelago.service;

import com.archipelago.dto.response.GraphSuggestionResponse;

import java.util.List;

public interface GraphSuggestionService {
    List<GraphSuggestionResponse> getSuggestions(
            Long movieId,
            Integer limit,
            String categories,
            boolean includeExisting
    );
}
