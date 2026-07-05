package com.archipelago.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.catalog.providers")
@Data
public class CatalogProviderProperties {
    private Provider curated = new Provider(true);
    private TmdbProvider tmdb = new TmdbProvider();

    @Data
    public static class Provider {
        private boolean enabled = true;

        public Provider() {
        }

        public Provider(boolean enabled) {
            this.enabled = enabled;
        }
    }

    @Data
    public static class TmdbProvider {
        private boolean enabled = false;
        private String apiKey;
        private String baseUrl = "https://api.themoviedb.org/3";
    }
}
