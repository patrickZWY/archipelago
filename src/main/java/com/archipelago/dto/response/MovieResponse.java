package com.archipelago.dto.response;

import com.archipelago.model.Movie;

import java.util.Arrays;
import java.util.List;

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
        String directorNotes
) {

    public static MovieResponse from(Movie movie) {
        return new MovieResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getReleaseYear(),
                movie.getDirector(),
                movie.getPictureUrl(),
                movie.getExternalId(),
                movie.getTagline(),
                movie.getSynopsis(),
                splitList(movie.getGenres()),
                movie.getRuntimeMinutes(),
                splitList(movie.getCastMembers()),
                movie.getDirectorNotes()
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
}
