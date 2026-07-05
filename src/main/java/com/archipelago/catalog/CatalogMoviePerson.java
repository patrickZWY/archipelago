package com.archipelago.catalog;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CatalogMoviePerson {
    private String providerId;
    private String source;
    private String personName;
    private String role;
    private Integer billingOrder;
}
