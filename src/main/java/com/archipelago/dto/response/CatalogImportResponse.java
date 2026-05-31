package com.archipelago.dto.response;

public record CatalogImportResponse(
        String source,
        int insertedCount,
        int updatedCount,
        int totalProcessed
) {
}
