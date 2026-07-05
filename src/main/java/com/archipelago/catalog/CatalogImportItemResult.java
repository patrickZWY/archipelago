package com.archipelago.catalog;

public record CatalogImportItemResult(
        CatalogImportAction action,
        Long movieId,
        String title,
        Integer releaseYear,
        String externalId,
        CatalogErrorKind errorKind,
        String message
) {
    public static CatalogImportItemResult inserted(Long movieId, ImportedMovie movie) {
        return success(CatalogImportAction.INSERTED, movieId, movie);
    }

    public static CatalogImportItemResult updated(Long movieId, ImportedMovie movie) {
        return success(CatalogImportAction.UPDATED, movieId, movie);
    }

    public static CatalogImportItemResult skipped(Long movieId, ImportedMovie movie) {
        return success(CatalogImportAction.SKIPPED, movieId, movie);
    }

    public static CatalogImportItemResult failed(ImportedMovie movie, CatalogErrorKind errorKind, String message) {
        return new CatalogImportItemResult(
                CatalogImportAction.FAILED,
                null,
                movie == null ? null : movie.title(),
                movie == null ? null : movie.releaseYear(),
                movie == null ? null : movie.externalId(),
                errorKind,
                message
        );
    }

    private static CatalogImportItemResult success(CatalogImportAction action, Long movieId, ImportedMovie movie) {
        return new CatalogImportItemResult(
                action,
                movieId,
                movie.title(),
                movie.releaseYear(),
                movie.externalId(),
                null,
                null
        );
    }
}
