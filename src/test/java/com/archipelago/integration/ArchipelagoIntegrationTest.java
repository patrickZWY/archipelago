package com.archipelago.integration;

import com.archipelago.mapper.UserMapper;
import com.archipelago.model.User;
import com.archipelago.model.enums.AccountStatus;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
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
        jdbcTemplate.update("DELETE FROM auth_audit_events");
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
    void registrationCreatesPendingAccountWithoutStartingSession() throws Exception {
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
                .andExpect(jsonPath("$.data.authenticated").value(false))
                .andExpect(jsonPath("$.data.user").doesNotExist())
                .andExpect(jsonPath("$.message").value("Check your email to finish signup"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) registerResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/auth/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(false));

        User user = userMapper.findActiveByEmail("user1@example.com").orElseThrow();
        assertThat(user.isVerified()).isFalse();
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(user.getVerificationTokenHash()).isNotBlank();
        assertThat(user.getVerificationTokenExpireTime()).isAfter(LocalDateTime.now());
    }

    @Test
    void loginSessionLogoutLifecycleWorksAfterVerification() throws Exception {
        MockHttpSession session = registerUser("user-login@example.com", "secret123", "user-login", fetchCsrf(null));

        mockMvc.perform(get("/api/auth/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.user.email").value("user-login@example.com"));

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
    void unverifiedUserCannotLogin() throws Exception {
        CsrfContext csrf = fetchCsrf(null);
        mockMvc.perform(post("/api/auth/register")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"pending@example.com","password":"secret123","username":"pending-user"}
                                """))
                .andExpect(status().isCreated());

        csrf = fetchCsrf(null);
        mockMvc.perform(post("/api/auth/login")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"pending@example.com","password":"secret123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void verificationTokenActivatesAccountAndIsSingleUseAndExpiring() throws Exception {
        registerPendingUser("verify@example.com", "secret123", "verify-user");
        User user = userMapper.findActiveByEmail("verify@example.com").orElseThrow();
        String token = "known-verification-token";
        jdbcTemplate.update(
                "UPDATE users SET verification_token_hash = ?, verification_token_expire_time = ? WHERE id = ?",
                tokenHash(token),
                Timestamp.valueOf(LocalDateTime.now().plusHours(1)),
                user.getId()
        );

        mockMvc.perform(get("/api/auth/verify?token={token}", token))
                .andExpect(status().isOk());

        User verified = userMapper.findActiveByEmail("verify@example.com").orElseThrow();
        assertThat(verified.isVerified()).isTrue();
        assertThat(verified.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(verified.getVerificationTokenHash()).isNull();

        mockMvc.perform(get("/api/auth/verify?token={token}", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid verification token"));

        registerPendingUser("expired-verify@example.com", "secret123", "expired-verify");
        User expired = userMapper.findActiveByEmail("expired-verify@example.com").orElseThrow();
        String expiredToken = "expired-verification-token";
        jdbcTemplate.update(
                "UPDATE users SET verification_token_hash = ?, verification_token_expire_time = ? WHERE id = ?",
                tokenHash(expiredToken),
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)),
                expired.getId()
        );

        mockMvc.perform(get("/api/auth/verify?token={token}", expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Verification token has expired"));
    }

    @Test
    void forgotPasswordRateLimitIsGeneric() throws Exception {
        for (int i = 0; i < 5; i++) {
            CsrfContext csrf = fetchCsrf(null);
            mockMvc.perform(post("/api/auth/forgot-password")
                            .session(csrf.session())
                            .cookie(csrf.cookie())
                            .header(csrf.headerName(), csrf.token())
                            .header("X-Forwarded-For", "203.0.113.77")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"rate-limit@example.com"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("If the account exists, a reset link has been issued"));
        }

        CsrfContext csrf = fetchCsrf(null);
        mockMvc.perform(post("/api/auth/forgot-password")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .header("X-Forwarded-For", "203.0.113.77")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"rate-limit@example.com"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many attempts. Try again later."));
    }

    @Test
    void authActionsWriteAuditEventsWithoutPlaintextRequestData() throws Exception {
        registerUser("audit@example.com", "secret123", "audit-user", fetchCsrf(null));

        CsrfContext csrf = fetchCsrf(null);
        mockMvc.perform(post("/api/auth/login")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .header("User-Agent", "AuditTest/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"audit@example.com","password":"wrongpass"}
                                """))
                .andExpect(status().isUnauthorized());

        Integer loginFailures = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_audit_events WHERE event_type = 'LOGIN' AND outcome = 'FAILURE'",
                Integer.class
        );
        assertThat(loginFailures).isNotNull().isGreaterThan(0);

        String ipHash = jdbcTemplate.queryForObject(
                "SELECT ip_hash FROM auth_audit_events WHERE event_type = 'LOGIN' AND outcome = 'FAILURE' ORDER BY id DESC LIMIT 1",
                String.class
        );
        assertThat(ipHash).isNotBlank();
        assertThat(ipHash).doesNotContain("127.0.0.1");
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
    void spaShellAndStaticAssetsArePublicWithoutChangingApiAuth() throws Exception {
        mockMvc.perform(get("/explore"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));

        mockMvc.perform(get("/shared/demo-token"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));

        mockMvc.perform(get("/assets/test.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string("static asset\n"));

        mockMvc.perform(get("/api/connections"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
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
        MockHttpSession originalSession = registerUser("reset@example.com", "secret123", "reset-user", csrf);

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
        assertThat(user.getPasswordResetTokenHash()).isNotBlank();
        assertThat(user.getPasswordResetTokenExpireTime()).isAfter(LocalDateTime.now());

        String resetToken = "known-reset-token";
        jdbcTemplate.update(
                "UPDATE users SET password_reset_token_hash = ?, password_reset_token_expire_time = ? WHERE id = ?",
                tokenHash(resetToken),
                Timestamp.valueOf(LocalDateTime.now().plusHours(1)),
                user.getId()
        );

        csrf = fetchCsrf(null);
        mockMvc.perform(post("/api/auth/reset-password")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordPayload(resetToken, "newsecret123"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/profile").session(originalSession))
                .andExpect(status().isUnauthorized());

        csrf = fetchCsrf(null);
        mockMvc.perform(post("/api/auth/reset-password")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordPayload(resetToken, "newsecret123"))))
                .andExpect(status().isUnauthorized());

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
    void forgotAndResetPasswordValidationAndTokenErrorsStayConsistent() throws Exception {
        CsrfContext csrf = fetchCsrf(null);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"bad-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.email").value("Email must be valid"));

        mockMvc.perform(post("/api/auth/reset-password")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"missing-token","newPassword":"newsecret123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid reset token"));

        registerUser("expired@example.com", "secret123", "expired-user", fetchCsrf(null));
        csrf = fetchCsrf(null);
        mockMvc.perform(post("/api/auth/forgot-password")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"expired@example.com"}
                                """))
                .andExpect(status().isOk());

        User expiredUser = userMapper.findActiveByEmail("expired@example.com").orElseThrow();
        jdbcTemplate.update(
                "UPDATE users SET password_reset_token_hash = ?, password_reset_token_expire_time = ? WHERE id = ?",
                tokenHash("expired-reset-token"),
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)),
                expiredUser.getId()
        );

        csrf = fetchCsrf(null);
        mockMvc.perform(post("/api/auth/reset-password")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordPayload("expired-reset-token", "newsecret123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Reset token has expired"));
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
                .andExpect(jsonPath("$.data.castMembers[0]").value("Alexander Kaidanovsky"))
                .andExpect(jsonPath("$.data.catalogGenres[0].name").value("Drama"))
                .andExpect(jsonPath("$.data.people[?(@.name == 'Alexander Kaidanovsky' && @.role == 'CAST')]").isNotEmpty())
                .andExpect(jsonPath("$.data.externalIds[0].type").value("imdb"))
                .andExpect(jsonPath("$.data.externalIds[0].value").value("tt0079944"));
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
    void demoSessionShowcasesCatalogMetadataFiltersAndSuggestions() throws Exception {
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

        mockMvc.perform(get("/api/movies/1").session(demoSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Inception"))
                .andExpect(jsonPath("$.data.externalIds[0].value").value("tt1375666"))
                .andExpect(jsonPath("$.data.catalogGenres[?(@.name == 'Heist')]").isNotEmpty())
                .andExpect(jsonPath("$.data.people[?(@.name == 'Michael Caine' && @.role == 'CAST')]").isNotEmpty());

        mockMvc.perform(get("/api/movies/search")
                        .param("person", "Michael Caine")
                        .param("graphStatus", "not_in_graph")
                        .session(demoSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.title == 'The Prestige')]").isNotEmpty())
                .andExpect(jsonPath("$.data[?(@.title == 'The Dark Knight')]").isNotEmpty());

        MvcResult suggestionsResult = mockMvc.perform(get("/api/graph-suggestions")
                        .param("movieId", "1")
                        .param("limit", "8")
                        .session(demoSession))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode suggestions = objectMapper.readTree(suggestionsResult.getResponse().getContentAsString()).path("data");
        boolean sawPrestigeSuggestion = false;
        boolean sawSharedDirector = false;
        boolean sawSharedCast = false;
        for (JsonNode suggestion : suggestions) {
            if (!"The Prestige".equals(suggestion.path("candidateMovie").path("title").asText())) {
                continue;
            }
            sawPrestigeSuggestion = true;
            assertThat(suggestion.path("existingEdge").asBoolean()).isFalse();
            assertThat(suggestion.path("category").asText()).isEqualTo("director");
            for (JsonNode evidence : suggestion.path("evidence")) {
                if ("SHARED_DIRECTOR".equals(evidence.path("type").asText())) {
                    sawSharedDirector = true;
                }
                if ("SHARED_CAST".equals(evidence.path("type").asText())) {
                    sawSharedCast = true;
                }
            }
        }

        assertThat(sawPrestigeSuggestion).isTrue();
        assertThat(sawSharedDirector).isTrue();
        assertThat(sawSharedCast).isTrue();
    }

    @Test
    void shareLifecycleListsOwnerSharesAndRevocationInvalidatesPublicAccess() throws Exception {
        MockHttpSession ownerSession = registerUser("share-owner@example.com", "secret123", "share-owner", fetchCsrf(null));
        MockHttpSession otherSession = registerUser("share-other@example.com", "secret123", "share-other", fetchCsrf(null));

        CsrfContext ownerCsrf = fetchCsrf(ownerSession);
        mockMvc.perform(post("/api/connections")
                        .session(ownerCsrf.session())
                        .cookie(ownerCsrf.cookie())
                        .header(ownerCsrf.headerName(), ownerCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMovieId":1,"toMovieId":2,"reason":"Shared dream logic","weight":1.1,"category":"theme"}
                                """))
                .andExpect(status().isCreated());

        ownerCsrf = fetchCsrf(ownerSession);
        MvcResult firstShareResult = mockMvc.perform(post("/api/shares")
                        .session(ownerCsrf.session())
                        .cookie(ownerCsrf.cookie())
                        .header(ownerCsrf.headerName(), ownerCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"movieId":1,"title":"Owner share"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String firstShareToken = objectMapper.readTree(firstShareResult.getResponse().getContentAsString())
                .path("data").path("shareToken").asText();

        ownerCsrf = fetchCsrf(ownerSession);
        mockMvc.perform(post("/api/shares")
                        .session(ownerCsrf.session())
                        .cookie(ownerCsrf.cookie())
                        .header(ownerCsrf.headerName(), ownerCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"movieId":2,"title":"Second owner share"}
                                """))
                .andExpect(status().isCreated());

        CsrfContext otherCsrf = fetchCsrf(otherSession);
        mockMvc.perform(post("/api/shares")
                        .session(otherCsrf.session())
                        .cookie(otherCsrf.cookie())
                        .header(otherCsrf.headerName(), otherCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"movieId":3,"title":"Other share"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/shares").session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].title").value("Second owner share"))
                .andExpect(jsonPath("$.data[0].rootMovieTitle").value("Interstellar"))
                .andExpect(jsonPath("$.data[1].title").value("Owner share"))
                .andExpect(jsonPath("$.data[1].rootMovieTitle").value("Inception"));

        otherCsrf = fetchCsrf(otherSession);
        mockMvc.perform(delete("/api/shares/{shareToken}", firstShareToken)
                        .session(otherCsrf.session())
                        .cookie(otherCsrf.cookie())
                        .header(otherCsrf.headerName(), otherCsrf.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Shared graph not found"));

        ownerCsrf = fetchCsrf(ownerSession);
        mockMvc.perform(delete("/api/shares/{shareToken}", firstShareToken)
                        .session(ownerCsrf.session())
                        .cookie(ownerCsrf.cookie())
                        .header(ownerCsrf.headerName(), ownerCsrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Shared graph revoked"));

        mockMvc.perform(get("/api/shares/{shareToken}", firstShareToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Shared graph not found"));

        mockMvc.perform(get("/api/shares").session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Second owner share"));
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
    void globalGraphWorkspaceEndpointsReturnFullScopesAndMergedFriendProvenance() throws Exception {
        MockHttpSession viewerSession = registerUser("global-viewer@example.com", "secret123", "global-viewer", fetchCsrf(null));
        MockHttpSession friendOneSession = registerUser("global-friend1@example.com", "secret123", "global-friend-one", fetchCsrf(null));
        MockHttpSession friendTwoSession = registerUser("global-friend2@example.com", "secret123", "global-friend-two", fetchCsrf(null));

        long friendOneUserId = userMapper.findActiveByEmail("global-friend1@example.com").orElseThrow().getId();
        long friendTwoUserId = userMapper.findActiveByEmail("global-friend2@example.com").orElseThrow().getId();

        CsrfContext viewerCsrf = fetchCsrf(viewerSession);
        mockMvc.perform(post("/api/connections")
                        .session(viewerCsrf.session())
                        .cookie(viewerCsrf.cookie())
                        .header(viewerCsrf.headerName(), viewerCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMovieId":1,"toMovieId":2,"reason":"Viewer component one","weight":1.2,"category":"theme"}
                                """))
                .andExpect(status().isCreated());

        viewerCsrf = fetchCsrf(viewerSession);
        mockMvc.perform(post("/api/connections")
                        .session(viewerCsrf.session())
                        .cookie(viewerCsrf.cookie())
                        .header(viewerCsrf.headerName(), viewerCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMovieId":4,"toMovieId":5,"reason":"Viewer component two","weight":1.8,"category":"structure"}
                                """))
                .andExpect(status().isCreated());

        CsrfContext friendOneCsrf = fetchCsrf(friendOneSession);
        mockMvc.perform(post("/api/connections")
                        .session(friendOneCsrf.session())
                        .cookie(friendOneCsrf.cookie())
                        .header(friendOneCsrf.headerName(), friendOneCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMovieId":1,"toMovieId":2,"reason":"Friend one overlap","weight":1.1,"category":"theme"}
                                """))
                .andExpect(status().isCreated());

        friendOneCsrf = fetchCsrf(friendOneSession);
        mockMvc.perform(post("/api/connections")
                        .session(friendOneCsrf.session())
                        .cookie(friendOneCsrf.cookie())
                        .header(friendOneCsrf.headerName(), friendOneCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMovieId":2,"toMovieId":3,"reason":"Friend one bridge","weight":1.3,"category":"structure"}
                                """))
                .andExpect(status().isCreated());

        CsrfContext friendTwoCsrf = fetchCsrf(friendTwoSession);
        mockMvc.perform(post("/api/connections")
                        .session(friendTwoCsrf.session())
                        .cookie(friendTwoCsrf.cookie())
                        .header(friendTwoCsrf.headerName(), friendTwoCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMovieId":2,"toMovieId":1,"reason":"Friend two overlap","weight":2.4,"category":"director"}
                                """))
                .andExpect(status().isCreated());

        friendTwoCsrf = fetchCsrf(friendTwoSession);
        mockMvc.perform(post("/api/connections")
                        .session(friendTwoCsrf.session())
                        .cookie(friendTwoCsrf.cookie())
                        .header(friendTwoCsrf.headerName(), friendTwoCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMovieId":3,"toMovieId":6,"reason":"Friend two extension","weight":1.6,"category":"theme"}
                                """))
                .andExpect(status().isCreated());

        viewerCsrf = fetchCsrf(viewerSession);
        MvcResult firstFriendRequest = mockMvc.perform(post("/api/friends/requests")
                        .session(viewerCsrf.session())
                        .cookie(viewerCsrf.cookie())
                        .header(viewerCsrf.headerName(), viewerCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientUserId":%d}
                                """.formatted(friendOneUserId)))
                .andExpect(status().isCreated())
                .andReturn();
        long firstRequestId = objectMapper.readTree(firstFriendRequest.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        friendOneCsrf = fetchCsrf(friendOneSession);
        mockMvc.perform(post("/api/friends/requests/{id}/accept", firstRequestId)
                        .session(friendOneCsrf.session())
                        .cookie(friendOneCsrf.cookie())
                        .header(friendOneCsrf.headerName(), friendOneCsrf.token()))
                .andExpect(status().isOk());

        viewerCsrf = fetchCsrf(viewerSession);
        MvcResult secondFriendRequest = mockMvc.perform(post("/api/friends/requests")
                        .session(viewerCsrf.session())
                        .cookie(viewerCsrf.cookie())
                        .header(viewerCsrf.headerName(), viewerCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientUserId":%d}
                                """.formatted(friendTwoUserId)))
                .andExpect(status().isCreated())
                .andReturn();
        long secondRequestId = objectMapper.readTree(secondFriendRequest.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        friendTwoCsrf = fetchCsrf(friendTwoSession);
        mockMvc.perform(post("/api/friends/requests/{id}/accept", secondRequestId)
                        .session(friendTwoCsrf.session())
                        .cookie(friendTwoCsrf.cookie())
                        .header(friendTwoCsrf.headerName(), friendTwoCsrf.token()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/global-graphs/me").session(viewerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.movies.length()").value(4))
                .andExpect(jsonPath("$.data.connections.length()").value(2));

        mockMvc.perform(get("/api/global-graphs/friends").session(viewerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(get("/api/global-graphs/friends/{friendUserId}", friendOneUserId).session(viewerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.movies.length()").value(3))
                .andExpect(jsonPath("$.data.connections.length()").value(2));

        MvcResult aggregateResult = mockMvc.perform(get("/api/global-graphs/friends/aggregate").session(viewerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.movies.length()").value(6))
                .andExpect(jsonPath("$.data.connections.length()").value(4))
                .andReturn();

        JsonNode aggregateConnections = objectMapper.readTree(aggregateResult.getResponse().getContentAsString())
                .path("data").path("connections");
        JsonNode mergedConnection = null;
        for (JsonNode connection : aggregateConnections) {
            if (connection.path("fromMovieId").asLong() == 1L && connection.path("toMovieId").asLong() == 2L) {
                mergedConnection = connection;
                break;
            }
        }
        assertThat(mergedConnection).isNotNull();
        assertThat(mergedConnection.path("aggregate").asBoolean()).isTrue();
        assertThat(mergedConnection.path("contributorCount").asInt()).isEqualTo(3);
        assertThat(mergedConnection.path("weight").asDouble()).isEqualTo(2.4);
        assertThat(mergedConnection.path("category").asText()).isEqualTo("theme");
        assertThat(mergedConnection.path("reason").asText()).isEqualTo("Merged from 3 graphs");
        assertThat(mergedConnection.path("contributors").get(0).path("username").asText()).isEqualTo("global-friend-one");
        assertThat(mergedConnection.path("contributors").get(1).path("username").asText()).isEqualTo("global-friend-two");
        assertThat(mergedConnection.path("contributors").get(2).path("username").asText()).isEqualTo("global-viewer");

        mockMvc.perform(get("/api/global-graphs/path?scope=all-friends&from=1&to=6").session(viewerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.movies.length()").value(4))
                .andExpect(jsonPath("$.data.connections.length()").value(3));

        mockMvc.perform(get("/api/global-graphs/path?scope=all-friends&from=1&to=5").session(viewerSession))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("These movies are not connected in the merged friend graph"));
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
        mockMvc.perform(post("/api/auth/register")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(email, password, username))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.authenticated").value(false));

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
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void registerPendingUser(String email, String password, String username) throws Exception {
        CsrfContext csrf = fetchCsrf(null);
        mockMvc.perform(post("/api/auth/register")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(email, password, username))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.authenticated").value(false));
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

    private String tokenHash(String token) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
    }
}
