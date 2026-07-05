package com.archipelago.integration;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogImportIntegrationTest {
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanCatalogTestData() {
        jdbcTemplate.update("DELETE FROM catalog_import_runs WHERE source LIKE 'test-curated-%'");
        jdbcTemplate.update("""
                DELETE FROM movies
                WHERE external_id IN ('ttpreviewboundary', 'ttapplyboundary1', 'ttapplyboundary2', 'ttinvalidboundary')
                   OR title IN ('Preview Boundary Movie', 'Apply Boundary One', 'Apply Boundary Two')
                """);
    }

    @Test
    void previewClassifiesCatalogWithoutMutatingMovies() throws Exception {
        MockHttpSession session = registerUser("catalog-preview@example.com", "catalog-preview");
        int moviesBefore = countMovies();

        CsrfContext csrf = fetchCsrf(session);
        mockMvc.perform(post("/api/movies/imports/preview?provider=curated&source=test-curated-preview")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("curated"))
                .andExpect(jsonPath("$.data.operation").value("PREVIEW"))
                .andExpect(jsonPath("$.data.runId").isNotEmpty())
                .andExpect(jsonPath("$.data.insertedCount").value(1))
                .andExpect(jsonPath("$.data.totalProcessed").value(1))
                .andExpect(jsonPath("$.data.results[0].action").value("INSERTED"));

        assertThat(countMovies()).isEqualTo(moviesBefore);
        assertThat(countMoviesByExternalId("ttpreviewboundary")).isZero();
        assertThat(countRuns("test-curated-preview", "PREVIEW", "SUCCEEDED")).isEqualTo(1);
    }

    @Test
    void applyImportIsIdempotentAndPersistsProviderMetadata() throws Exception {
        MockHttpSession session = registerUser("catalog-apply@example.com", "catalog-apply");

        CsrfContext csrf = fetchCsrf(session);
        mockMvc.perform(post("/api/movies/imports/apply?provider=curated&source=test-curated-apply")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operation").value("APPLY"))
                .andExpect(jsonPath("$.data.insertedCount").value(2))
                .andExpect(jsonPath("$.data.updatedCount").value(0))
                .andExpect(jsonPath("$.data.skippedCount").value(0))
                .andExpect(jsonPath("$.data.failedCount").value(0));

        csrf = fetchCsrf(session);
        mockMvc.perform(post("/api/movies/imports/apply?provider=curated&source=test-curated-apply")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.insertedCount").value(0))
                .andExpect(jsonPath("$.data.updatedCount").value(0))
                .andExpect(jsonPath("$.data.skippedCount").value(2))
                .andExpect(jsonPath("$.data.failedCount").value(0));

        assertThat(countMoviesByExternalId("ttapplyboundary1")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movie_external_ids WHERE provider_id = 'curated' AND source = 'test-curated-apply'",
                Integer.class
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movie_genres WHERE provider_id = 'curated' AND source = 'test-curated-apply'",
                Integer.class
        )).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movie_people WHERE provider_id = 'curated' AND source = 'test-curated-apply' AND role = 'CAST'",
                Integer.class
        )).isEqualTo(3);
    }

    @Test
    void invalidSourceReturnsTypedCatalogError() throws Exception {
        MockHttpSession session = registerUser("catalog-invalid-source@example.com", "catalog-invalid-source");

        CsrfContext csrf = fetchCsrf(session);
        mockMvc.perform(post("/api/movies/imports/preview?provider=curated&source=missing-source")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errorKind").value("INVALID_INPUT"));
    }

    @Test
    void missingProviderReturnsTypedCatalogError() throws Exception {
        MockHttpSession session = registerUser("catalog-missing-provider@example.com", "catalog-missing-provider");

        CsrfContext csrf = fetchCsrf(session);
        mockMvc.perform(post("/api/movies/imports/preview?provider=missing&source=test-curated-preview")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.errorKind").value("PROVIDER_UNAVAILABLE"));
    }

    @Test
    void invalidCatalogItemIsReportedWithoutCreatingMovie() throws Exception {
        MockHttpSession session = registerUser("catalog-failed-item@example.com", "catalog-failed-item");

        CsrfContext csrf = fetchCsrf(session);
        mockMvc.perform(post("/api/movies/imports/apply?provider=curated&source=test-curated-invalid")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalProcessed").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.results[0].action").value("FAILED"))
                .andExpect(jsonPath("$.data.results[0].errorKind").value("PERMANENT_PROVIDER_DATA_ERROR"));

        assertThat(countMoviesByExternalId("ttinvalidboundary")).isZero();
        assertThat(countRuns("test-curated-invalid", "APPLY", "COMPLETED_WITH_FAILURES")).isEqualTo(1);
    }

    @Test
    void movieSearchFiltersUseNormalizedMetadataAndCurrentUserGraphStatus() throws Exception {
        MockHttpSession session = registerUser("catalog-search@example.com", "catalog-search");
        CsrfContext csrf = fetchCsrf(session);
        mockMvc.perform(post("/api/movies/imports/apply?provider=curated&source=test-curated-apply")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk());

        long applyOneMovieId = movieIdByExternalId("ttapplyboundary1");

        mockMvc.perform(get("/api/movies/search").param("person", "Apply Actor Two").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.title == 'Apply Boundary One')]").isNotEmpty())
                .andExpect(jsonPath("$.data[?(@.title == 'Apply Boundary Two')]").isEmpty());

        mockMvc.perform(get("/api/movies/search?genre=Second").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.title == 'Apply Boundary Two')]").isNotEmpty())
                .andExpect(jsonPath("$.data[?(@.title == 'Apply Boundary One')]").isEmpty());

        mockMvc.perform(get("/api/movies/search?q=Apply&year=2026").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.title == 'Apply Boundary One')]").isNotEmpty())
                .andExpect(jsonPath("$.data[?(@.title == 'Apply Boundary Two')]").isEmpty());

        csrf = fetchCsrf(session);
        mockMvc.perform(post("/api/connections")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMovieId":1,"toMovieId":%d,"reason":"Search graph status fixture","weight":1.0,"category":"theme"}
                                """.formatted(applyOneMovieId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/movies/search?q=Apply&graphStatus=in_graph").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.title == 'Apply Boundary One')]").isNotEmpty())
                .andExpect(jsonPath("$.data[?(@.title == 'Apply Boundary Two')]").isEmpty());

        mockMvc.perform(get("/api/movies/search?q=Apply&graphStatus=not_in_graph").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.title == 'Apply Boundary One')]").isEmpty())
                .andExpect(jsonPath("$.data[?(@.title == 'Apply Boundary Two')]").isNotEmpty());
    }

    @Test
    void movieSearchRejectsInvalidFilterValues() throws Exception {
        MockHttpSession session = registerUser("catalog-search-invalid@example.com", "catalog-search-invalid");

        mockMvc.perform(get("/api/movies/search?year=twenty").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Year must be a four-digit number"));

        mockMvc.perform(get("/api/movies/search?graphStatus=maybe").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Graph status must be all, in_graph, or not_in_graph"));
    }

    @Test
    void graphSuggestionsUseExplainableMetadataAndAvoidDuplicateEdges() throws Exception {
        MockHttpSession session = registerUser("catalog-suggestions@example.com", "catalog-suggestions");
        CsrfContext csrf = fetchCsrf(session);
        mockMvc.perform(post("/api/movies/imports/apply?provider=curated&source=test-curated-apply")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk());

        long applyOneMovieId = movieIdByExternalId("ttapplyboundary1");
        long applyTwoMovieId = movieIdByExternalId("ttapplyboundary2");

        mockMvc.perform(get("/api/graph-suggestions")
                        .param("movieId", String.valueOf(applyOneMovieId))
                        .param("categories", "genre")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].candidateMovie.id").value(applyTwoMovieId))
                .andExpect(jsonPath("$.data[0].category").value("genre"))
                .andExpect(jsonPath("$.data[0].confidence").value(0.08))
                .andExpect(jsonPath("$.data[0].evidence[0].type").value("SHARED_GENRE"))
                .andExpect(jsonPath("$.data[0].evidence[0].values[0]").value("Test"))
                .andExpect(jsonPath("$.data[0].existingEdge").value(false));

        csrf = fetchCsrf(session);
        mockMvc.perform(post("/api/connections")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMovieId":%d,"toMovieId":%d,"reason":"Suggested genre edge","weight":1.0,"category":"genre"}
                                """.formatted(applyOneMovieId, applyTwoMovieId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/graph-suggestions")
                        .param("movieId", String.valueOf(applyOneMovieId))
                        .param("categories", "genre")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get("/api/graph-suggestions")
                        .param("movieId", String.valueOf(applyOneMovieId))
                        .param("categories", "genre")
                        .param("includeExisting", "true")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].candidateMovie.id").value(applyTwoMovieId))
                .andExpect(jsonPath("$.data[0].existingEdge").value(true));
    }

    @Test
    void graphSuggestionsRejectInvalidInput() throws Exception {
        MockHttpSession session = registerUser("catalog-suggestions-invalid@example.com", "catalog-suggestions-invalid");

        mockMvc.perform(get("/api/graph-suggestions?movieId=1&limit=0").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Limit must be between 1 and 25"));

        mockMvc.perform(get("/api/graph-suggestions?movieId=1&categories=not-real").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Categories must be one or more supported graph categories"));
    }

    private MockHttpSession registerUser(String email, String username) throws Exception {
        CsrfContext csrf = fetchCsrf(null);
        mockMvc.perform(post("/api/auth/register")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"secret123","username":"%s"}
                                """.formatted(email, username)))
                .andExpect(status().isCreated());

        jdbcTemplate.update(
                "UPDATE users SET verified = TRUE, enabled = TRUE, account_status = 'ACTIVE', " +
                        "verification_token_hash = NULL, verification_token_expire_time = NULL WHERE LOWER(email) = LOWER(?)",
                email
        );

        CsrfContext loginCsrf = fetchCsrf(null);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .session(loginCsrf.session())
                        .cookie(loginCsrf.cookie())
                        .header(loginCsrf.headerName(), loginCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"secret123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private CsrfContext fetchCsrf(MockHttpSession session) throws Exception {
        MvcResult result = (session == null
                ? mockMvc.perform(get("/api/auth/session"))
                : mockMvc.perform(get("/api/auth/session").session(session)))
                .andExpect(status().isOk())
                .andReturn();
        String token = result.getResponse().getCookie(CSRF_COOKIE_NAME).getValue();
        MockHttpSession currentSession = (MockHttpSession) result.getRequest().getSession(false);
        return new CsrfContext(
                currentSession == null ? new MockHttpSession() : currentSession,
                result.getResponse().getCookie(CSRF_COOKIE_NAME),
                CSRF_HEADER_NAME,
                token
        );
    }

    private int countMovies() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM movies", Integer.class);
    }

    private int countMoviesByExternalId(String externalId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM movies WHERE external_id = ?", Integer.class, externalId);
    }

    private long movieIdByExternalId(String externalId) {
        return jdbcTemplate.queryForObject("SELECT id FROM movies WHERE external_id = ?", Long.class, externalId);
    }

    private int countRuns(String source, String operation, String status) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM catalog_import_runs WHERE source = ? AND operation = ? AND status = ?",
                Integer.class,
                source,
                operation,
                status
        );
    }

    private record CsrfContext(MockHttpSession session, Cookie cookie, String headerName, String token) {
    }
}
