package com.archipelago.catalog;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CatalogMovieGenre {
    private String providerId;
    private String source;
    private String genre;
}
