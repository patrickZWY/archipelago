package com.archipelago.controller;

import com.archipelago.dto.response.ConnectionResponse;
import com.archipelago.dto.response.MovieConnectionsResponse;
import com.archipelago.dto.response.MovieResponse;
import com.archipelago.model.Movie;
import com.archipelago.service.ConnectionService;
import com.archipelago.service.MovieService;
import com.archipelago.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<MovieResponse>>> searchMovies(@RequestParam("q") String query) {
        List<MovieResponse> results = movieService.searchMovies(query).stream()
                .map(MovieResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(results, "Movies retrieved"));
    }

    @GetMapping("/{movieId}/connections")
    public ResponseEntity<ApiResponse<MovieConnectionsResponse>> getMovieConnections(@PathVariable Long movieId) {
        Movie movie = movieService.getMovieById(movieId);
        List<ConnectionResponse> connections = connectionService.getConnectionsForCurrentUserByMovieComponent(movieId).stream()
                .map(ConnectionResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(
                new MovieConnectionsResponse(MovieResponse.from(movie), connections),
                "Movie graph retrieved"
        ));
    }
}
