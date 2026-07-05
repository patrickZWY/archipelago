package com.archipelago.dto.response;

import com.archipelago.catalog.CatalogErrorKind;

public record CatalogErrorResponse(
        CatalogErrorKind errorKind
) {
}
