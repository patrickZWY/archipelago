package com.archipelago.service.impl;

import com.archipelago.dto.response.CatalogImportResponse;
import com.archipelago.exception.ResourceNotFoundException;
import com.archipelago.mapper.MovieMapper;
import com.archipelago.model.Movie;
import com.archipelago.service.MovieService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MovieServiceImpl implements MovieService {
    private static final int SEARCH_LIMIT = 25;

    private final MovieMapper movieMapper;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

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
        String sanitizedSource = StringUtils.hasText(source) ? source.trim() : "curated-spring-2026";
        Resource resource = resourceLoader.getResource("classpath:catalog/" + sanitizedSource + ".json");
        if (!resource.exists()) {
            throw new ResourceNotFoundException("Catalog source not found");
        }

        List<ImportedMoviePayload> payloads;
        try (InputStream inputStream = resource.getInputStream()) {
            payloads = objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read catalog source", exception);
        }

        int insertedCount = 0;
        int updatedCount = 0;
        for (ImportedMoviePayload payload : payloads) {
            Movie movie = payload.toMovie();
            Movie existing = resolveExistingMovie(movie);
            if (existing == null) {
                movieMapper.insert(movie);
                insertedCount++;
                continue;
            }

            movie.setId(existing.getId());
            movieMapper.update(movie);
            updatedCount++;
        }

        return new CatalogImportResponse(sanitizedSource, insertedCount, updatedCount, payloads.size());
    }

    private Movie resolveExistingMovie(Movie candidate) {
        if (StringUtils.hasText(candidate.getExternalId())) {
            Movie byExternalId = movieMapper.findByExternalId(candidate.getExternalId()).orElse(null);
            if (byExternalId != null) {
                return byExternalId;
            }
        }
        return movieMapper.findByTitleAndReleaseYear(candidate.getTitle(), candidate.getReleaseYear()).orElse(null);
    }

    private record ImportedMoviePayload(
            String title,
            int releaseYear,
            String director,
            String pictureUrl,
            String externalId,
            String tagline,
            String synopsis,
            List<String> genres,
            Integer runtimeMinutes,
            List<String> castMembers,
            String directorNotes
    ) {
        private Movie toMovie() {
            return Movie.builder()
                    .title(title)
                    .releaseYear(releaseYear)
                    .director(director)
                    .pictureUrl(pictureUrl)
                    .externalId(externalId)
                    .tagline(tagline)
                    .synopsis(synopsis)
                    .genres(join(genres))
                    .runtimeMinutes(runtimeMinutes)
                    .castMembers(join(castMembers))
                    .directorNotes(directorNotes)
                    .build();
        }

        private static String join(List<String> values) {
            if (values == null || values.isEmpty()) {
                return null;
            }
            return values.stream()
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse(null);
        }
    }
}
