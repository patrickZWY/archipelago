package com.archipelago.catalog;

import java.util.Set;

public interface CatalogProvider {
    String providerId();

    Set<CatalogCapability> capabilities();

    CatalogImportResult previewImport(CatalogImportRequest request);

    CatalogImportResult applyImport(CatalogImportRequest request);
}
