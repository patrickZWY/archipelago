package com.archipelago.dto.response;

import com.archipelago.catalog.CatalogErrorKind;
import com.archipelago.catalog.CatalogImportAction;
import com.archipelago.catalog.CatalogImportItemResult;

public record CatalogImportItemResponse(
        CatalogImportAction action,
        Long movieId,
        String title,
        Integer releaseYear,
        String externalId,
        CatalogErrorKind errorKind,
        String message
) {
    public static CatalogImportItemResponse from(CatalogImportItemResult result) {
        return new CatalogImportItemResponse(
                result.action(),
                result.movieId(),
                result.title(),
                result.releaseYear(),
                result.externalId(),
                result.errorKind(),
                result.message()
        );
    }
}
