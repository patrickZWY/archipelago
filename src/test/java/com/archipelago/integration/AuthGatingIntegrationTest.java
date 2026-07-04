package com.archipelago.integration;

import com.archipelago.mapper.UserMapper;
import com.archipelago.model.User;
import com.archipelago.model.enums.AccountStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.auth.signup-mode=approval",
        "app.auth.rate-limit.login=500",
        "app.auth.rate-limit.register=500",
        "app.auth.rate-limit.verify=500",
        "app.auth.rate-limit.demo=500"
})
@AutoConfigureMockMvc
class AuthGatingIntegrationTest {
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
    void approvalModeRequiresVerificationThenApprovalButDemoStaysPublic() throws Exception {
        CsrfContext csrf = fetchCsrf(null);
        mockMvc.perform(post("/api/auth/register")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload("approval@example.com", "secret123", "approval-user"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.authenticated").value(false));

        User user = userMapper.findActiveByEmail("approval@example.com").orElseThrow();
        String token = "approval-verification-token";
        jdbcTemplate.update(
                "UPDATE users SET verification_token_hash = ?, verification_token_expire_time = ? WHERE id = ?",
                tokenHash(token),
                Timestamp.valueOf(LocalDateTime.now().plusHours(1)),
                user.getId()
        );

        mockMvc.perform(get("/api/auth/verify?token={token}", token))
                .andExpect(status().isOk());

        User verified = userMapper.findActiveByEmail("approval@example.com").orElseThrow();
        assertThat(verified.isVerified()).isTrue();
        assertThat(verified.getAccountStatus()).isEqualTo(AccountStatus.PENDING_APPROVAL);

        csrf = fetchCsrf(null);
        mockMvc.perform(post("/api/auth/login")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"approval@example.com","password":"secret123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        csrf = fetchCsrf(null);
        mockMvc.perform(post("/api/auth/demo")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.user.username").value("demo"));
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

    private String tokenHash(String token) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    private record CsrfContext(MockHttpSession session, Cookie cookie, String headerName, String token) {
    }

    private record RegisterPayload(String email, String password, String username) {
    }
}
