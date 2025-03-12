package com.archipelago.service.impl;

import com.archipelago.exception.ResourceNotFoundException;
import com.archipelago.mapper.MovieMapper;
import com.archipelago.model.Movie;
import com.archipelago.service.MovieService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MovieServiceImpl implements MovieService {
    private static final Logger logger = LoggerFactory.getLogger(MovieServiceImpl.class);
    private final MovieMapper movieMapper;

    @Override
    public boolean movieExists(Long id) {
        logger.info("Checking if movie {} exists", id);
        boolean exists = movieMapper.findById(id).isPresent();
        logger.info("Movie {} exists", id);
        return exists;
    }

    @Override
    public Movie getMovieById(Long id) {
        logger.info("Getting movie {}", id);
        Movie result =  movieMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with id: " + id));
        logger.info("Movie {} gotten", id);
        return result;
    }

    @Override
    public List<Movie> searchMovies(String title) {
        logger.info("Searching for movie {} exists", title);
        List<Movie> results = movieMapper.searchMovies(title);
        logger.info("Movie(s) for {} found", title);
        return results;
    }
}
