package com.archipelago.controller;

import com.archipelago.model.Movie;
import com.archipelago.service.MovieService;
import com.archipelago.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
public class MovieController {
    private final Logger logger = LoggerFactory.getLogger(MovieController.class);
    private final MovieService movieService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<Movie>>> searchMovies(@RequestParam String title) {
        logger.info("Search movies");
        List<Movie> movies = movieService.searchMovies(title);
        logger.info("Found {} movies", movies.size());
        return ResponseEntity.ok(ApiResponse.success(movies, "Movies retrieved success."));
    }

}
