package com.archipelago.dto.response;

import com.archipelago.catalog.CatalogMoviePerson;

public record MoviePersonResponse(
        String provider,
        String source,
        String name,
        String role,
        int billingOrder
) {
    public static MoviePersonResponse from(CatalogMoviePerson person) {
        return new MoviePersonResponse(
                person.getProviderId(),
                person.getSource(),
                person.getPersonName(),
                person.getRole(),
                person.getBillingOrder() == null ? 0 : person.getBillingOrder()
        );
    }
}
