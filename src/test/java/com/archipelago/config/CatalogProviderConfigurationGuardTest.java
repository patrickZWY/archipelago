package com.archipelago.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogProviderConfigurationGuardTest {

    @Test
    void acceptsCuratedProviderWithTmdbDisabled() {
        CatalogProviderProperties properties = new CatalogProviderProperties();

        assertThatCode(() -> new CatalogProviderConfigurationGuard(properties).run(null))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDisabledCuratedProvider() {
        CatalogProviderProperties properties = new CatalogProviderProperties();
        properties.getCurated().setEnabled(false);

        assertThatThrownBy(() -> new CatalogProviderConfigurationGuard(properties).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("curated catalog provider");
    }

    @Test
    void rejectsEnabledTmdbProviderWithoutApiKey() {
        CatalogProviderProperties properties = new CatalogProviderProperties();
        properties.getTmdb().setEnabled(true);

        assertThatThrownBy(() -> new CatalogProviderConfigurationGuard(properties).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TMDb catalog provider requires");
    }

    @Test
    void rejectsEnabledTmdbProviderWithInvalidBaseUrl() {
        CatalogProviderProperties properties = new CatalogProviderProperties();
        properties.getTmdb().setEnabled(true);
        properties.getTmdb().setApiKey("test-key");
        properties.getTmdb().setBaseUrl("not-a-url");

        assertThatThrownBy(() -> new CatalogProviderConfigurationGuard(properties).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("valid HTTP(S) base URL");
    }
}
