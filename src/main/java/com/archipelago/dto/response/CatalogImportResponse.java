package com.archipelago.dto.response;

import com.archipelago.catalog.CatalogImportOperation;
import com.archipelago.catalog.CatalogImportResult;

import java.util.List;

public record CatalogImportResponse(
        String source,
        int insertedCount,
        int updatedCount,
        int totalProcessed,
        String provider,
        String runId,
        CatalogImportOperation operation,
        int skippedCount,
        int failedCount,
        List<CatalogImportItemResponse> results
) {
    public static CatalogImportResponse from(CatalogImportResult result) {
        return new CatalogImportResponse(
                result.source(),
                result.insertedCount(),
                result.updatedCount(),
                result.totalProcessed(),
                result.provider(),
                result.runId(),
                result.operation(),
                result.skippedCount(),
                result.failedCount(),
                result.results().stream()
                        .map(CatalogImportItemResponse::from)
                        .toList()
        );
    }
}
