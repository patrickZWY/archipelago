package com.archipelago.integration;

import com.archipelago.mapper.UserMapper;
import com.archipelago.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        jdbcTemplate.update("DELETE FROM connections");
        jdbcTemplate.update("DELETE FROM users");
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

        mockMvc.perform(get("/api/movies/1/connections").session(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connections.length()").value(1));

        mockMvc.perform(get("/api/movies/1/connections").session(user2Session))
                .andExpect(status().isOk())
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
                .andExpect(jsonPath("$.data.length()").value(0));
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
