package com.archipelago.catalog;

import com.archipelago.mapper.CatalogImportMapper;
import com.archipelago.mapper.MovieMapper;
import com.archipelago.model.Movie;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class CuratedCatalogProvider implements CatalogProvider {
    public static final String PROVIDER_ID = "curated";
    public static final String DEFAULT_SOURCE = "curated-spring-2026";
    private static final String EXTERNAL_ID_TYPE_IMDB = "imdb";
    private static final String ROLE_DIRECTOR = "DIRECTOR";
    private static final String ROLE_CAST = "CAST";
    private static final Pattern SAFE_SOURCE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private final MovieMapper movieMapper;
    private final CatalogImportMapper catalogImportMapper;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public Set<CatalogCapability> capabilities() {
        return Set.of(
                CatalogCapability.MOVIE_METADATA,
                CatalogCapability.PEOPLE_CAST,
                CatalogCapability.IMAGES
        );
    }

    @Override
    public CatalogImportResult previewImport(CatalogImportRequest request) {
        return process(request, CatalogImportOperation.PREVIEW, false);
    }

    @Override
    public CatalogImportResult applyImport(CatalogImportRequest request) {
        return process(request, CatalogImportOperation.APPLY, true);
    }

    private CatalogImportResult process(CatalogImportRequest request, CatalogImportOperation operation, boolean mutate) {
        String source = resolveSource(request.source());
        List<ImportedMovie> importedMovies = loadSource(source);
        List<CatalogImportItemResult> results = new ArrayList<>();

        for (ImportedMovie importedMovie : importedMovies) {
            if (!importedMovie.hasRequiredMovieFields()) {
                results.add(CatalogImportItemResult.failed(
                        importedMovie,
                        CatalogErrorKind.PERMANENT_PROVIDER_DATA_ERROR,
                        "Catalog movie requires title, releaseYear, and director"
                ));
                continue;
            }

            try {
                Movie movie = importedMovie.toMovie();
                Movie existingMovie = resolveExistingMovie(movie);
                if (existingMovie == null) {
                    if (mutate) {
                        movieMapper.insert(movie);
                        replaceProviderMetadata(movie.getId(), source, importedMovie, movie);
                    }
                    results.add(CatalogImportItemResult.inserted(movie.getId(), importedMovie));
                    continue;
                }

                movie.setId(existingMovie.getId());
                boolean changed = hasMovieChanges(existingMovie, movie);
                if (mutate) {
                    if (changed) {
                        movieMapper.update(movie);
                    }
                    replaceProviderMetadata(existingMovie.getId(), source, importedMovie, movie);
                }
                results.add(changed
                        ? CatalogImportItemResult.updated(existingMovie.getId(), importedMovie)
                        : CatalogImportItemResult.skipped(existingMovie.getId(), importedMovie));
            } catch (CatalogItemException exception) {
                results.add(CatalogImportItemResult.failed(
                        importedMovie,
                        exception.errorKind(),
                        exception.getMessage()
                ));
            }
        }

        return new CatalogImportResult(PROVIDER_ID, source, request.runId(), operation, results);
    }

    private String resolveSource(String source) {
        String resolvedSource = StringUtils.hasText(source) ? source.trim() : DEFAULT_SOURCE;
        if (!SAFE_SOURCE_NAME.matcher(resolvedSource).matches() || resolvedSource.contains("..")) {
            throw new CatalogException(
                    CatalogErrorKind.INVALID_INPUT,
                    HttpStatus.BAD_REQUEST,
                    "Catalog source is invalid"
            );
        }
        return resolvedSource;
    }

    private List<ImportedMovie> loadSource(String source) {
        Resource resource = resourceLoader.getResource("classpath:catalog/" + source + ".json");
        if (!resource.exists()) {
            throw new CatalogException(
                    CatalogErrorKind.INVALID_INPUT,
                    HttpStatus.BAD_REQUEST,
                    "Catalog source not found"
            );
        }

        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        } catch (IOException exception) {
            throw new CatalogException(
                    CatalogErrorKind.PERMANENT_PROVIDER_DATA_ERROR,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Catalog source could not be read",
                    exception
            );
        }
    }

    private Movie resolveExistingMovie(Movie candidate) {
        Movie byNormalizedExternalId = null;
        Movie byLegacyExternalId = null;
        if (StringUtils.hasText(candidate.getExternalId())) {
            Long movieId = catalogImportMapper.findMovieIdByProviderExternalId(
                    PROVIDER_ID,
                    EXTERNAL_ID_TYPE_IMDB,
                    candidate.getExternalId()
            );
            if (movieId != null) {
                byNormalizedExternalId = movieMapper.findById(movieId).orElse(null);
            }
            byLegacyExternalId = movieMapper.findByExternalId(candidate.getExternalId()).orElse(null);
        }

        Movie byTitle = movieMapper.findByTitleAndReleaseYear(
                candidate.getTitle(),
                candidate.getReleaseYear()
        ).orElse(null);

        assertSameMovieIfBothPresent(byNormalizedExternalId, byLegacyExternalId);
        Movie byExternalId = byNormalizedExternalId != null ? byNormalizedExternalId : byLegacyExternalId;
        assertSameMovieIfBothPresent(byExternalId, byTitle);
        return byExternalId != null ? byExternalId : byTitle;
    }

    private void assertSameMovieIfBothPresent(Movie first, Movie second) {
        if (first != null && second != null && !Objects.equals(first.getId(), second.getId())) {
            throw new CatalogItemException(
                    CatalogErrorKind.IMPORT_CONFLICT,
                    "Catalog external id conflicts with an existing movie"
            );
        }
    }

    private boolean hasMovieChanges(Movie existingMovie, Movie importedMovie) {
        return !Objects.equals(existingMovie.getTitle(), importedMovie.getTitle())
                || existingMovie.getReleaseYear() != importedMovie.getReleaseYear()
                || !Objects.equals(existingMovie.getDirector(), importedMovie.getDirector())
                || !Objects.equals(existingMovie.getPictureUrl(), importedMovie.getPictureUrl())
                || !Objects.equals(existingMovie.getExternalId(), importedMovie.getExternalId())
                || !Objects.equals(existingMovie.getTagline(), importedMovie.getTagline())
                || !Objects.equals(existingMovie.getSynopsis(), importedMovie.getSynopsis())
                || !Objects.equals(existingMovie.getGenres(), importedMovie.getGenres())
                || !Objects.equals(existingMovie.getRuntimeMinutes(), importedMovie.getRuntimeMinutes())
                || !Objects.equals(existingMovie.getCastMembers(), importedMovie.getCastMembers())
                || !Objects.equals(existingMovie.getDirectorNotes(), importedMovie.getDirectorNotes());
    }

    private void replaceProviderMetadata(Long movieId, String source, ImportedMovie importedMovie, Movie movie) {
        catalogImportMapper.deleteExternalIdsForMovieSource(movieId, PROVIDER_ID, source);
        if (StringUtils.hasText(movie.getExternalId())) {
            Long externalOwnerMovieId = catalogImportMapper.findMovieIdByProviderExternalId(
                    PROVIDER_ID,
                    EXTERNAL_ID_TYPE_IMDB,
                    movie.getExternalId()
            );
            if (externalOwnerMovieId != null && !Objects.equals(externalOwnerMovieId, movieId)) {
                throw new CatalogItemException(
                        CatalogErrorKind.IMPORT_CONFLICT,
                        "Catalog external id conflicts with an existing movie"
                );
            }
            catalogImportMapper.deleteExternalIdByProviderExternalId(
                    PROVIDER_ID,
                    EXTERNAL_ID_TYPE_IMDB,
                    movie.getExternalId()
            );
            catalogImportMapper.insertExternalId(
                    movieId,
                    PROVIDER_ID,
                    source,
                    EXTERNAL_ID_TYPE_IMDB,
                    movie.getExternalId()
            );
        }

        catalogImportMapper.deleteGenresForMovieSource(movieId, PROVIDER_ID, source);
        for (String genre : importedMovie.cleanGenres()) {
            catalogImportMapper.insertGenre(movieId, PROVIDER_ID, source, genre);
        }

        catalogImportMapper.deletePeopleForMovieSource(movieId, PROVIDER_ID, source);
        catalogImportMapper.insertPerson(movieId, PROVIDER_ID, source, movie.getDirector(), ROLE_DIRECTOR, 0);
        List<String> castMembers = importedMovie.cleanCastMembers();
        for (int index = 0; index < castMembers.size(); index++) {
            catalogImportMapper.insertPerson(movieId, PROVIDER_ID, source, castMembers.get(index), ROLE_CAST, index + 1);
        }
    }

    private static final class CatalogItemException extends RuntimeException {
        private final CatalogErrorKind errorKind;

        private CatalogItemException(CatalogErrorKind errorKind, String message) {
            super(message);
            this.errorKind = errorKind;
        }

        private CatalogErrorKind errorKind() {
            return errorKind;
        }
    }
}
