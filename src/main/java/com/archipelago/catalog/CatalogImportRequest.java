package com.archipelago.catalog;

public record CatalogImportRequest(
        String provider,
        String source,
        String runId
) {
}
