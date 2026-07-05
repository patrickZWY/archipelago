package com.archipelago.controller;

import com.archipelago.catalog.CatalogImportService;
import com.archipelago.dto.request.MovieSearchCriteria;
import com.archipelago.dto.response.CatalogImportResponse;
import com.archipelago.dto.response.MovieConnectionsResponse;
import com.archipelago.dto.response.MoviePathResponse;
import com.archipelago.dto.response.MovieResponse;
import com.archipelago.security.CurrentUserProvider;
import com.archipelago.service.ConnectionService;
import com.archipelago.service.GraphAccessService;
import com.archipelago.service.MovieService;
import com.archipelago.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;
    private final ConnectionService connectionService;
    private final GraphAccessService graphAccessService;
    private final CurrentUserProvider currentUserProvider;
    private final CatalogImportService catalogImportService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<MovieResponse>>> searchMovies(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "person", required = false) String person,
            @RequestParam(name = "genre", required = false) String genre,
            @RequestParam(name = "year", required = false) String year,
            @RequestParam(name = "graphStatus", required = false) String graphStatus
    ) {
        MovieSearchCriteria criteria = MovieSearchCriteria.from(
                query,
                person,
                genre,
                year,
                graphStatus,
                currentUserProvider.getCurrentUser().getId()
        );
        List<MovieResponse> results = movieService.searchMovies(criteria).stream()
                .map(MovieResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(results, "Movies retrieved"));
    }

    @PostMapping("/imports/curated")
    public ResponseEntity<ApiResponse<CatalogImportResponse>> importCuratedCatalog(
            @RequestParam(name = "source", required = false) String source
    ) {
        CatalogImportResponse response = movieService.importCuratedCatalog(source);
        return ResponseEntity.ok(ApiResponse.success(response, "Catalog import completed"));
    }

    @PostMapping("/imports/preview")
    public ResponseEntity<ApiResponse<CatalogImportResponse>> previewCatalogImport(
            @RequestParam(name = "provider", required = false, defaultValue = "curated") String provider,
            @RequestParam(name = "source", required = false) String source
    ) {
        CatalogImportResponse response = catalogImportService.previewImport(provider, source);
        return ResponseEntity.ok(ApiResponse.success(response, "Catalog import preview completed"));
    }

    @PostMapping("/imports/apply")
    public ResponseEntity<ApiResponse<CatalogImportResponse>> applyCatalogImport(
            @RequestParam(name = "provider", required = false, defaultValue = "curated") String provider,
            @RequestParam(name = "source", required = false) String source
    ) {
        CatalogImportResponse response = catalogImportService.applyImport(provider, source);
        return ResponseEntity.ok(ApiResponse.success(response, "Catalog import completed"));
    }

    @GetMapping("/{movieId}")
    public ResponseEntity<ApiResponse<MovieResponse>> getMovie(@PathVariable Long movieId) {
        return ResponseEntity.ok(ApiResponse.success(movieService.getMovieDetailsById(movieId), "Movie retrieved"));
    }

    @GetMapping("/{movieId}/connections")
    public ResponseEntity<ApiResponse<MovieConnectionsResponse>> getMovieConnections(@PathVariable Long movieId) {
        return ResponseEntity.ok(ApiResponse.success(
                graphAccessService.getMovieGraph(currentUserProvider.getCurrentUser().getId(), movieId),
                "Movie graph retrieved"
        ));
    }

    @GetMapping("/path")
    public ResponseEntity<ApiResponse<MoviePathResponse>> getMoviePath(
            @RequestParam("from") Long fromMovieId,
            @RequestParam("to") Long toMovieId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                connectionService.getShortestPathForCurrentUser(fromMovieId, toMovieId),
                "Movie path retrieved"
        ));
    }
}
