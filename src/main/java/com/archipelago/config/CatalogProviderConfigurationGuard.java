package com.archipelago.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;

@Component
@RequiredArgsConstructor
public class CatalogProviderConfigurationGuard implements ApplicationRunner {
    private final CatalogProviderProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.getCurated().isEnabled()) {
            throw new IllegalStateException("The curated catalog provider must remain enabled");
        }

        CatalogProviderProperties.TmdbProvider tmdb = properties.getTmdb();
        if (!tmdb.isEnabled()) {
            return;
        }

        if (!StringUtils.hasText(tmdb.getApiKey())) {
            throw new IllegalStateException("TMDb catalog provider requires app.catalog.providers.tmdb.api-key when enabled");
        }
        if (!StringUtils.hasText(tmdb.getBaseUrl()) || !isValidHttpUrl(tmdb.getBaseUrl())) {
            throw new IllegalStateException("TMDb catalog provider requires a valid HTTP(S) base URL");
        }
    }

    private boolean isValidHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
