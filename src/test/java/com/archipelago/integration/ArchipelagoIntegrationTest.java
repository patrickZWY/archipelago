package com.archipelago.integration;

import com.archipelago.mapper.UserMapper;
import com.archipelago.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ArchipelagoIntegrationTest {
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";
    private static final String DEMO_USER_EMAILS = "'demo@archipelago.local', 'demo.friend.one@archipelago.local', 'demo.friend.two@archipelago.local'";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserMapper userMapper;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM shared_graph_exports");
        jdbcTemplate.update(
                "DELETE FROM friendships " +
                        "WHERE requester_user_id NOT IN (SELECT id FROM users WHERE email IN (" + DEMO_USER_EMAILS + ")) " +
                        "OR recipient_user_id NOT IN (SELECT id FROM users WHERE email IN (" + DEMO_USER_EMAILS + "))"
        );
        jdbcTemplate.update("DELETE FROM connections WHERE user_id NOT IN (SELECT id FROM users WHERE email IN (" + DEMO_USER_EMAILS + "))");
        jdbcTemplate.update("DELETE FROM users WHERE email NOT IN (" + DEMO_USER_EMAILS + ")");
    }

    @Test
    void registerSessionLogoutLifecycleWorks() throws Exception {
        CsrfContext csrf = fetchCsrf(null);

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user1@example.com","password":"secret123","username":"user1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andReturn();

        MockHttpSession session = (MockHttpSession) registerResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/auth/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.user.email").value("user1@example.com"));

        CsrfContext authenticatedCsrf = fetchCsrf(session);
        mockMvc.perform(post("/api/auth/logout")
                        .session(authenticatedCsrf.session())
                        .cookie(authenticatedCsrf.cookie())
                        .header(authenticatedCsrf.headerName(), authenticatedCsrf.token()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(false));
    }

    @Test
    void csrfIsRequiredForMutatingRoutes() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user2@example.com","password":"secret123","username":"user2"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void registerValidationErrorsUseFieldKeyedApiEnvelope() throws Exception {
        CsrfContext csrf = fetchCsrf(null);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"invalid-email","password":"short","username":"xy"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.email").value("Email must be valid"))
                .andExpect(jsonPath("$.data.password").value("Password must be between 8 and 128 characters long"))
                .andExpect(jsonPath("$.data.username").value("Username must be between 3 and 50 characters long"))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.isObject()).isTrue();
        assertThat(data.fieldNames()).toIterable().contains("email", "password", "username");
    }

    @Test
    void loginValidationErrorsUseFieldKeyedApiEnvelope() throws Exception {
        CsrfContext csrf = fetchCsrf(null);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"invalid-email","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.email").value("Email must be valid"))
                .andExpect(jsonPath("$.data.password").value("Password is required"))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.isObject()).isTrue();
        assertThat(data.fieldNames()).toIterable().contains("email", "password");
    }

    @Test
    void forgotResetPasswordFlowUpdatesCredentials() throws Exception {
        CsrfContext csrf = fetchCsrf(null);
        registerUser("reset@example.com", "secret123", "reset-user", csrf);

        csrf = fetchCsrf(null);
        mockMvc.perform(post("/api/auth/forgot-password")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"reset@example.com"}
                                """))
                .andExpect(status().isOk());

        User user = userMapper.findActiveByEmail("reset@example.com").orElseThrow();
        assertThat(user.getPasswordResetToken()).isNotBlank();

        csrf = fetchCsrf(null);
        mockMvc.perform(post("/api/auth/reset-password")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordPayload(user.getPasswordResetToken(), "newsecret123"))))
                .andExpect(status().isOk());

        csrf = fetchCsrf(null);
        mockMvc.perform(post("/api/auth/login")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"reset@example.com","password":"newsecret123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true));
    }

    @Test
    void profileUpdateAndDeleteWorkAgainstAuthenticatedSession() throws Exception {
        CsrfContext csrf = fetchCsrf(null);
        MockHttpSession session = registerUser("profile@example.com", "secret123", "profile-user", csrf);

        csrf = fetchCsrf(session);
        mockMvc.perform(put("/api/users/profile")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"renamed-user","password":"secret456"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("renamed-user"));

        csrf = fetchCsrf(session);
        mockMvc.perform(delete("/api/users/profile")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk());

        csrf = fetchCsrf(null);
        mockMvc.perform(post("/api/auth/login")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"profile@example.com","password":"secret456"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void connectionCrudIsScopedToCurrentUserAndMovie() throws Exception {
        MockHttpSession user1Session = registerUser("graph1@example.com", "secret123", "graph-user1", fetchCsrf(null));
        MockHttpSession user2Session = registerUser("graph2@example.com", "secret123", "graph-user2", fetchCsrf(null));

        CsrfContext user1Csrf = fetchCsrf(user1Session);
        MvcResult createResult = mockMvc.perform(post("/api/connections")
                        .session(user1Csrf.session())
                        .cookie(user1Csrf.cookie())
                        .header(user1Csrf.headerName(), user1Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMovieId":1,"toMovieId":2,"reason":"Shared Nolan themes","weight":2.0,"category":"director"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fromMovieId").value(1))
                .andReturn();

        long connectionId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        user1Csrf = fetchCsrf(user1Session);
        mockMvc.perform(post("/api/connections")
                        .session(user1Csrf.session())
                        .cookie(user1Csrf.cookie())
                        .header(user1Csrf.headerName(), user1Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMovieId":2,"toMovieId":3,"reason":"Memory puzzle lineage","weight":1.2,"category":"structure"}
                                """))
                .andExpect(status().isCreated());

        user1Csrf = fetchCsrf(user1Session);
        mockMvc.perform(post("/api/connections")
                        .session(user1Csrf.session())
                        .cookie(user1Csrf.cookie())
                        .header(user1Csrf.headerName(), user1Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMovieId":4,"toMovieId":5,"reason":"Separate branch","weight":1.0,"category":"genre"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/movies/1/connections").session(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Movie graph retrieved"))
                .andExpect(jsonPath("$.data.movie.id").value(1))
                .andExpect(jsonPath("$.data.movies.length()").value(3))
                .andExpect(jsonPath("$.data.connections.length()").value(2))
                .andExpect(jsonPath("$.data.connections[?(@.fromMovieId == 1 && @.toMovieId == 2)]").isNotEmpty())
                .andExpect(jsonPath("$.data.connections[?(@.fromMovieId == 2 && @.toMovieId == 3)]").isNotEmpty())
                .andExpect(jsonPath("$.data.connections[?(@.fromMovieId == 4 || @.toMovieId == 4 || @.fromMovieId == 5 || @.toMovieId == 5)]").isEmpty());

        mockMvc.perform(get("/api/movies/1/connections").session(user2Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.movie.id").value(1))
                .andExpect(jsonPath("$.data.connections.length()").value(0));

        user1Csrf = fetchCsrf(user1Session);
        mockMvc.perform(put("/api/connections/{id}", connectionId)
                        .session(user1Csrf.session())
                        .cookie(user1Csrf.cookie())
                        .header(user1Csrf.headerName(), user1Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Updated reason","weight":1.5,"category":"tone"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reason").value("Updated reason"));

        CsrfContext user2Csrf = fetchCsrf(user2Session);
        mockMvc.perform(delete("/api/connections/{id}", connectionId)
                        .session(user2Csrf.session())
                        .cookie(user2Csrf.cookie())
                        .header(user2Csrf.headerName(), user2Csrf.token()))
                .andExpect(status().isNotFound());

        user1Csrf = fetchCsrf(user1Session);
        mockMvc.perform(delete("/api/connections/{id}", connectionId)
                        .session(user1Csrf.session())
                        .cookie(user1Csrf.cookie())
                        .header(user1Csrf.headerName(), user1Csrf.token()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/connections").session(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.id == %s)]", connectionId).isEmpty());
    }

    @Test
    void connectionCategoryValidationAndPathStayUserScoped() throws Exception {
        MockHttpSession user1Session = registerUser("path1@example.com", "secret123", "path-user1", fetchCsrf(null));
        MockHttpSession user2Session = registerUser("path2@example.com", "secret123", "path-user2", fetchCsrf(null));

        CsrfContext user1Csrf = fetchCsrf(user1Session);
        mockMvc.perform(post("/api/connections")
                        .session(user1Csrf.session())
                        .cookie(user1Csrf.cookie())
                        .header(user1Csrf.headerName(), user1Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMovieId":1,"toMovieId":2,"reason":"Shared Nolan themes","weight":2.0,"category":"director"}
                                """))
                .andExpect(status().isCreated());

        user1Csrf = fetchCsrf(user1Session);
        mockMvc.perform(post("/api/connections")
                        .session(user1Csrf.session())
                        .cookie(user1Csrf.cookie())
                        .header(user1Csrf.headerName(), user1Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMovieId":2,"toMovieId":3,"reason":"Memory puzzle lineage","weight":1.2,"category":"structure"}
                                """))
                .andExpect(status().isCreated());

        user1Csrf = fetchCsrf(user1Session);
        mockMvc.perform(post("/api/connections")
                        .session(user1Csrf.session())
                        .cookie(user1Csrf.cookie())
                        .header(user1Csrf.headerName(), user1Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMovieId":3,"toMovieId":6,"reason":"Atmospheric dread","weight":0.9,"category":"not-real"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Category must be one of the supported graph categories"));

        mockMvc.perform(get("/api/movies/path?from=1&to=3").session(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Movie path retrieved"))
                .andExpect(jsonPath("$.data.movies.length()").value(3))
                .andExpect(jsonPath("$.data.connections.length()").value(2))
                .andExpect(jsonPath("$.data.connections[0].category").value("director"))
                .andExpect(jsonPath("$.data.connections[1].category").value("structure"));

        mockMvc.perform(get("/api/movies/path?from=1&to=3").session(user2Session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("These movies are not connected in your graph"));
    }

    @Test
    void curatedCatalogImportExpandsSearchAndMovieDetails() throws Exception {
        MockHttpSession session = registerUser("catalog@example.com", "secret123", "catalog-user", fetchCsrf(null));

        CsrfContext csrf = fetchCsrf(session);
        mockMvc.perform(post("/api/movies/imports/curated?source=curated-spring-2026")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("curated-spring-2026"))
                .andExpect(jsonPath("$.data.insertedCount").value(6))
                .andExpect(jsonPath("$.data.totalProcessed").value(6));

        MvcResult searchResult = mockMvc.perform(get("/api/movies/search?q=Stalker").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Stalker"))
                .andReturn();

        long movieId = objectMapper.readTree(searchResult.getResponse().getContentAsString())
                .path("data").get(0).path("id").asLong();

        mockMvc.perform(get("/api/movies/{movieId}", movieId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tagline").value("Let your weakness melt into the room."))
                .andExpect(jsonPath("$.data.genres[0]").value("Science Fiction"))
                .andExpect(jsonPath("$.data.castMembers[0]").value("Alexander Kaidanovsky"));
    }

    @Test
    void demoSessionAndExplicitSharedGraphViewWork() throws Exception {
        CsrfContext csrf = fetchCsrf(null);
        MvcResult demoResult = mockMvc.perform(post("/api/auth/demo")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.user.username").value("demo"))
                .andReturn();

        MockHttpSession demoSession = (MockHttpSession) demoResult.getRequest().getSession(false);
        assertThat(demoSession).isNotNull();

        csrf = fetchCsrf(demoSession);
        MvcResult shareResult = mockMvc.perform(post("/api/shares")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"movieId":1,"title":"Demo Nolan cluster"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Demo Nolan cluster"))
                .andExpect(jsonPath("$.data.graph.connections.length()").value(4))
                .andReturn();

        String shareToken = objectMapper.readTree(shareResult.getResponse().getContentAsString())
                .path("data").path("shareToken").asText();

        mockMvc.perform(get("/api/shares/{shareToken}", shareToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Demo Nolan cluster"))
                .andExpect(jsonPath("$.data.graph.movie.title").value("Inception"))
                .andExpect(jsonPath("$.data.graph.connections.length()").value(4))
                .andExpect(jsonPath("$.data.graph.movies.length()").value(5));
    }

    @Test
    void demoSessionIncludesSeededFriendNetwork() throws Exception {
        CsrfContext csrf = fetchCsrf(null);
        MvcResult demoResult = mockMvc.perform(post("/api/auth/demo")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value("demo"))
                .andReturn();

        MockHttpSession demoSession = (MockHttpSession) demoResult.getRequest().getSession(false);
        assertThat(demoSession).isNotNull();

        MvcResult friendsResult = mockMvc.perform(get("/api/friends").session(demoSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn();

        JsonNode friends = objectMapper.readTree(friendsResult.getResponse().getContentAsString()).path("data");
        long friendUserId = friends.get(0).path("id").asLong();

        MvcResult profileResult = mockMvc.perform(get("/api/friends/{friendUserId}/profile", friendUserId).session(demoSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.movies.length()").value(3))
                .andReturn();

        long movieId = objectMapper.readTree(profileResult.getResponse().getContentAsString())
                .path("data").path("movies").get(0).path("id").asLong();

        mockMvc.perform(get("/api/friends/{friendUserId}/movies/{movieId}/connections", friendUserId, movieId).session(demoSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connections.length()").value(2))
                .andExpect(jsonPath("$.data.movies.length()").value(3));
    }

    @Test
    void friendshipLifecycleAndFriendsOnlyGraphBrowsingWork() throws Exception {
        MockHttpSession user1Session = registerUser("friend1@example.com", "secret123", "friend-one", fetchCsrf(null));
        MockHttpSession user2Session = registerUser("friend2@example.com", "secret123", "friend-two", fetchCsrf(null));

        CsrfContext user1Csrf = fetchCsrf(user1Session);
        mockMvc.perform(post("/api/connections")
                        .session(user1Csrf.session())
                        .cookie(user1Csrf.cookie())
                        .header(user1Csrf.headerName(), user1Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMovieId":1,"toMovieId":2,"reason":"Shared dream logic","weight":1.1,"category":"theme"}
                                """))
                .andExpect(status().isCreated());

        user1Csrf = fetchCsrf(user1Session);
        mockMvc.perform(post("/api/connections")
                        .session(user1Csrf.session())
                        .cookie(user1Csrf.cookie())
                        .header(user1Csrf.headerName(), user1Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMovieId":2,"toMovieId":3,"reason":"Memory architecture","weight":1.4,"category":"structure"}
                                """))
                .andExpect(status().isCreated());

        MvcResult searchResult = mockMvc.perform(get("/api/users/search?q=friend-two").session(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].username").value("friend-two"))
                .andExpect(jsonPath("$.data[0].email").doesNotExist())
                .andReturn();

        long friendUserId = objectMapper.readTree(searchResult.getResponse().getContentAsString())
                .path("data").get(0).path("id").asLong();

        user1Csrf = fetchCsrf(user1Session);
        MvcResult requestResult = mockMvc.perform(post("/api/friends/requests")
                        .session(user1Csrf.session())
                        .cookie(user1Csrf.cookie())
                        .header(user1Csrf.headerName(), user1Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientUserId":%d}
                                """.formatted(friendUserId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();

        long requestId = objectMapper.readTree(requestResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(get("/api/friends/requests").session(user2Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.incoming.length()").value(1))
                .andExpect(jsonPath("$.data.outgoing.length()").value(0))
                .andExpect(jsonPath("$.data.incoming[0].otherUser.username").value("friend-one"));

        mockMvc.perform(get("/api/friends/%d/profile".formatted(friendUserId)).session(user1Session))
                .andExpect(status().isForbidden());

        CsrfContext user2Csrf = fetchCsrf(user2Session);
        mockMvc.perform(post("/api/friends/requests/{id}/accept", requestId)
                        .session(user2Csrf.session())
                        .cookie(user2Csrf.cookie())
                        .header(user2Csrf.headerName(), user2Csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        mockMvc.perform(get("/api/friends").session(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].username").value("friend-two"))
                .andExpect(jsonPath("$.data[0].email").doesNotExist());

        mockMvc.perform(get("/api/friends").session(user2Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].username").value("friend-one"));

        mockMvc.perform(get("/api/friends/%d/profile".formatted(friendUserId)).session(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("friend-two"))
                .andExpect(jsonPath("$.data.movies").isArray());

        long user1Id = userMapper.findActiveByEmail("friend1@example.com").orElseThrow().getId();

        mockMvc.perform(get("/api/friends/{friendUserId}/profile", user1Id).session(user2Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("friend-one"))
                .andExpect(jsonPath("$.data.movies.length()").value(3));

        mockMvc.perform(get("/api/friends/{friendUserId}/movies/{movieId}/connections", user1Id, 1).session(user2Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.movie.id").value(1))
                .andExpect(jsonPath("$.data.movies.length()").value(3))
                .andExpect(jsonPath("$.data.connections.length()").value(2));

        mockMvc.perform(get("/api/friends/{friendUserId}/movies/path?from=1&to=3", user1Id).session(user2Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.movies.length()").value(3))
                .andExpect(jsonPath("$.data.connections.length()").value(2));

        user2Csrf = fetchCsrf(user2Session);
        mockMvc.perform(delete("/api/friends/{friendUserId}", user1Id)
                        .session(user2Csrf.session())
                        .cookie(user2Csrf.cookie())
                        .header(user2Csrf.headerName(), user2Csrf.token()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/friends/{friendUserId}/profile", user1Id).session(user2Session))
                .andExpect(status().isForbidden());
    }

    @Test
    void friendRequestDeclineAndDuplicateValidationWork() throws Exception {
        MockHttpSession user1Session = registerUser("decline1@example.com", "secret123", "decline-one", fetchCsrf(null));
        MockHttpSession user2Session = registerUser("decline2@example.com", "secret123", "decline-two", fetchCsrf(null));

        long user1Id = userMapper.findActiveByEmail("decline1@example.com").orElseThrow().getId();
        long user2Id = userMapper.findActiveByEmail("decline2@example.com").orElseThrow().getId();

        CsrfContext user1Csrf = fetchCsrf(user1Session);
        mockMvc.perform(post("/api/friends/requests")
                        .session(user1Csrf.session())
                        .cookie(user1Csrf.cookie())
                        .header(user1Csrf.headerName(), user1Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientUserId":%d}
                                """.formatted(user1Id)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot send a friend request to yourself"));

        MvcResult requestResult = mockMvc.perform(post("/api/friends/requests")
                        .session(user1Csrf.session())
                        .cookie(user1Csrf.cookie())
                        .header(user1Csrf.headerName(), user1Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientUserId":%d}
                                """.formatted(user2Id)))
                .andExpect(status().isCreated())
                .andReturn();

        long requestId = objectMapper.readTree(requestResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(post("/api/friends/requests")
                        .session(user1Csrf.session())
                        .cookie(user1Csrf.cookie())
                        .header(user1Csrf.headerName(), user1Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientUserId":%d}
                                """.formatted(user2Id)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Friend request already sent"));

        CsrfContext user2Csrf = fetchCsrf(user2Session);
        mockMvc.perform(post("/api/friends/requests/{id}/decline", requestId)
                        .session(user2Csrf.session())
                        .cookie(user2Csrf.cookie())
                        .header(user2Csrf.headerName(), user2Csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DECLINED"));

        user1Csrf = fetchCsrf(user1Session);
        mockMvc.perform(post("/api/friends/requests")
                        .session(user1Csrf.session())
                        .cookie(user1Csrf.cookie())
                        .header(user1Csrf.headerName(), user1Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientUserId":%d}
                                """.formatted(user2Id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    private MockHttpSession registerUser(String email, String password, String username, CsrfContext csrf) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(email, password, username))))
                .andExpect(status().isCreated())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private CsrfContext fetchCsrf(MockHttpSession session) throws Exception {
        MvcResult result = (session == null ? mockMvc.perform(get("/api/auth/session")) : mockMvc.perform(get("/api/auth/session").session(session)))
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

    private record CsrfContext(MockHttpSession session, Cookie cookie, String headerName, String token) {
    }

    private record RegisterPayload(String email, String password, String username) {
    }

    private record ResetPasswordPayload(String token, String newPassword) {
    }
}
