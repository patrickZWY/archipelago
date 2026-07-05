package com.archipelago.dto.response;

import com.archipelago.catalog.CatalogMovieGenre;

public record MovieGenreResponse(
        String provider,
        String source,
        String name
) {
    public static MovieGenreResponse from(CatalogMovieGenre genre) {
        return new MovieGenreResponse(
                genre.getProviderId(),
                genre.getSource(),
                genre.getGenre()
        );
    }
}
