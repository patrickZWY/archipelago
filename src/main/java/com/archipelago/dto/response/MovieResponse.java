package com.archipelago.dto.response;

import com.archipelago.catalog.CatalogMovieExternalId;
import com.archipelago.catalog.CatalogMovieGenre;
import com.archipelago.catalog.CatalogMoviePerson;
import com.archipelago.model.Movie;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record MovieResponse(
        Long id,
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
        String directorNotes,
        List<MovieGenreResponse> catalogGenres,
        List<MoviePersonResponse> people,
        List<MovieExternalIdResponse> externalIds
) {

    public static MovieResponse from(Movie movie) {
        return from(movie, List.of(), List.of(), List.of());
    }

    public static MovieResponse from(
            Movie movie,
            List<CatalogMovieGenre> catalogGenres,
            List<CatalogMoviePerson> people,
            List<CatalogMovieExternalId> externalIds
    ) {
        return new MovieResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getReleaseYear(),
                movie.getDirector(),
                movie.getPictureUrl(),
                movie.getExternalId(),
                movie.getTagline(),
                movie.getSynopsis(),
                mergeGenres(movie.getGenres(), catalogGenres),
                movie.getRuntimeMinutes(),
                mergeCastMembers(movie.getCastMembers(), people),
                movie.getDirectorNotes(),
                catalogGenres.stream().map(MovieGenreResponse::from).toList(),
                people.stream().map(MoviePersonResponse::from).toList(),
                externalIds.stream().map(MovieExternalIdResponse::from).toList()
        );
    }

    private static List<String> splitList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\s*,\\s*"))
                .map(String::trim)
                .filter(entry -> !entry.isBlank())
                .toList();
    }

    private static List<String> mergeGenres(String legacyGenres, List<CatalogMovieGenre> catalogGenres) {
        Map<String, String> merged = new LinkedHashMap<>();
        for (String genre : splitList(legacyGenres)) {
            merged.putIfAbsent(normalize(genre), genre);
        }
        for (CatalogMovieGenre genre : catalogGenres) {
            if (genre.getGenre() != null && !genre.getGenre().isBlank()) {
                merged.putIfAbsent(normalize(genre.getGenre()), genre.getGenre());
            }
        }
        return List.copyOf(merged.values());
    }

    private static List<String> mergeCastMembers(String legacyCastMembers, List<CatalogMoviePerson> people) {
        Map<String, String> merged = new LinkedHashMap<>();
        for (String castMember : splitList(legacyCastMembers)) {
            merged.putIfAbsent(normalize(castMember), castMember);
        }
        for (CatalogMoviePerson person : people) {
            if ("CAST".equalsIgnoreCase(person.getRole())
                    && person.getPersonName() != null
                    && !person.getPersonName().isBlank()) {
                merged.putIfAbsent(normalize(person.getPersonName()), person.getPersonName());
            }
        }
        return List.copyOf(merged.values());
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
