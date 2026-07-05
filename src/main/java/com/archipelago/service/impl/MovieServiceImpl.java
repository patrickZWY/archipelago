package com.archipelago.service.impl;

import com.archipelago.catalog.CatalogImportService;
import com.archipelago.dto.response.CatalogImportResponse;
import com.archipelago.exception.ResourceNotFoundException;
import com.archipelago.mapper.MovieMapper;
import com.archipelago.model.Movie;
import com.archipelago.service.MovieService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MovieServiceImpl implements MovieService {
    private static final int SEARCH_LIMIT = 25;

    private final MovieMapper movieMapper;
    private final CatalogImportService catalogImportService;

    @Override
    public boolean movieExists(Long id) {
        return movieMapper.findById(id).isPresent();
    }

    @Override
    public Movie getMovieById(Long id) {
        return movieMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found"));
    }

    @Override
    public List<Movie> searchMovies(String title) {
        if (!StringUtils.hasText(title)) {
            return movieMapper.findAll().stream().limit(SEARCH_LIMIT).toList();
        }
        return movieMapper.searchMovies(title.trim(), SEARCH_LIMIT);
    }

    @Override
    public CatalogImportResponse importCuratedCatalog(String source) {
        return catalogImportService.applyImport("curated", source);
    }
}
