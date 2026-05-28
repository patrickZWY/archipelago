package com.archipelago.dto.response;

import com.archipelago.model.Movie;

public record MovieResponse(
        Long id,
        String title,
        int releaseYear,
        String director,
        String pictureUrl,
        String externalId
) {

    public static MovieResponse from(Movie movie) {
        return new MovieResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getReleaseYear(),
                movie.getDirector(),
                movie.getPictureUrl(),
                movie.getExternalId()
        );
    }
}
