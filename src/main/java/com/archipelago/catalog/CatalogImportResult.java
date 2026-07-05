package com.archipelago.catalog;

import java.util.List;

public record CatalogImportResult(
        String provider,
        String source,
        String runId,
        CatalogImportOperation operation,
        List<CatalogImportItemResult> results
) {
    public CatalogImportResult {
        results = List.copyOf(results);
    }

    public int insertedCount() {
        return count(CatalogImportAction.INSERTED);
    }

    public int updatedCount() {
        return count(CatalogImportAction.UPDATED);
    }

    public int skippedCount() {
        return count(CatalogImportAction.SKIPPED);
    }

    public int failedCount() {
        return count(CatalogImportAction.FAILED);
    }

    public int totalProcessed() {
        return results.size();
    }

    public CatalogErrorKind firstErrorKind() {
        return results.stream()
                .map(CatalogImportItemResult::errorKind)
                .filter(errorKind -> errorKind != null)
                .findFirst()
                .orElse(null);
    }

    private int count(CatalogImportAction action) {
        return (int) results.stream()
                .filter(result -> result.action() == action)
                .count();
    }
}
