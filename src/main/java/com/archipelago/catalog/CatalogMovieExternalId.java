package com.archipelago.catalog;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CatalogMovieExternalId {
    private String providerId;
    private String source;
    private String externalIdType;
    private String externalId;
}
