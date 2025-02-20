package com.archipelago.service.impl;

import com.archipelago.exception.ResourceNotFoundException;
import com.archipelago.mapper.MovieMapper;
import com.archipelago.model.Movie;
import com.archipelago.repository.MovieRepository;
import com.archipelago.service.MovieService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MovieServiceImpl implements MovieService {
    private final MovieMapper movieMapper;

    @Override
    public boolean movieExists(Long id) {
        return movieMapper.findById(id).isPresent();
    }

    @Override
    public Movie getMovieById(Long id) {
        return movieMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with id: " + id));
    }

    @Override
    public List<Movie> searchMovies(String title) {
        return movieMapper.searchMovies(title);
    }
}
