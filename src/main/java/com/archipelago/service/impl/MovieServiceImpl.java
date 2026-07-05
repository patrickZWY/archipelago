package com.archipelago.service.impl;

import com.archipelago.catalog.CatalogImportService;
import com.archipelago.dto.request.MovieSearchCriteria;
import com.archipelago.dto.response.CatalogImportResponse;
import com.archipelago.dto.response.MovieResponse;
import com.archipelago.exception.ResourceNotFoundException;
import com.archipelago.mapper.CatalogMetadataMapper;
import com.archipelago.mapper.MovieMapper;
import com.archipelago.model.Movie;
import com.archipelago.model.enums.MovieGraphStatus;
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
    private final CatalogMetadataMapper catalogMetadataMapper;
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
    public MovieResponse getMovieDetailsById(Long id) {
        Movie movie = getMovieById(id);
        return MovieResponse.from(
                movie,
                catalogMetadataMapper.findGenresByMovieId(id),
                catalogMetadataMapper.findPeopleByMovieId(id),
                catalogMetadataMapper.findExternalIdsByMovieId(id)
        );
    }

    @Override
    public List<Movie> searchMovies(String title) {
        return searchMovies(new MovieSearchCriteria(
                StringUtils.hasText(title) ? title.trim() : null,
                null,
                null,
                null,
                MovieGraphStatus.ALL,
                null
        ));
    }

    @Override
    public List<Movie> searchMovies(MovieSearchCriteria criteria) {
        if (!StringUtils.hasText(criteria.query()) && !criteria.hasFilters()) {
            return movieMapper.findAll().stream().limit(SEARCH_LIMIT).toList();
        }
        return movieMapper.searchMoviesFiltered(
                criteria.query(),
                criteria.person(),
                criteria.genre(),
                criteria.releaseYear(),
                criteria.graphStatus().name(),
                criteria.userId(),
                SEARCH_LIMIT
        );
    }

    @Override
    public CatalogImportResponse importCuratedCatalog(String source) {
        return catalogImportService.applyImport("curated", source);
    }
}
