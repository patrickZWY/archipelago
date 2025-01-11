package com.archipelago.service;

import com.archipelago.model.Movie;

import java.util.List;

public interface MovieService {
    boolean movieExists(Long id);
    Movie getMovieById(Long id);
    List<Movie> searchMovies(String title);
}

