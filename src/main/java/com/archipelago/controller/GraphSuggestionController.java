package com.archipelago.controller;

import com.archipelago.dto.response.GraphSuggestionResponse;
import com.archipelago.service.GraphSuggestionService;
import com.archipelago.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/graph-suggestions")
@RequiredArgsConstructor
public class GraphSuggestionController {
    private final GraphSuggestionService graphSuggestionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<GraphSuggestionResponse>>> getSuggestions(
            @RequestParam("movieId") Long movieId,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "categories", required = false) String categories,
            @RequestParam(name = "includeExisting", required = false, defaultValue = "false") boolean includeExisting
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                graphSuggestionService.getSuggestions(movieId, limit, categories, includeExisting),
                "Graph suggestions retrieved"
        ));
    }
}
