package com.archipelago.dto.response;

import com.archipelago.catalog.CatalogMovieExternalId;

public record MovieExternalIdResponse(
        String provider,
        String source,
        String type,
        String value
) {
    public static MovieExternalIdResponse from(CatalogMovieExternalId externalId) {
        return new MovieExternalIdResponse(
                externalId.getProviderId(),
                externalId.getSource(),
                externalId.getExternalIdType(),
                externalId.getExternalId()
        );
    }
}
