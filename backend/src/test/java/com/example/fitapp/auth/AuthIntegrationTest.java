package com.example.fitapp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers;
import com.example.fitapp.support.TestUsers.Session;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;

    private ResultActions postJson(String path, Object body) throws Exception {
        return mvc.perform(
                post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    @Test
    void registerReturnsTokensAndCreatesProfile() throws Exception {
        Session s = users().register("Alice Test");

        mvc.perform(get("/api/me/profile").header("Authorization", s.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Test"))
                .andExpect(jsonPath("$.profileComplete").value(false));
    }

    @Test
    void registerResponseDoesNotLeakPasswordHash() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        postJson(
                        "/api/auth/register",
                        Map.of(
                                "email", "leak_" + suffix + "@example.com",
                                "password", TestUsers.PASSWORD,
                                "username", "leak_" + suffix,
                                "name", "Leak"))
                .andExpect(status().isCreated())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password"))))
                .andExpect(jsonPath("$.expiresInSeconds").value(900));
    }

    @Test
    void passwordIsStoredAsBcryptAndRefreshTokenIsStoredHashed() throws Exception {
        Session s = users().register("Hash Check");

        User stored = userRepository.findByEmail(s.email()).orElseThrow();
        assertThat(stored.getPasswordHash()).startsWith("$2");
        assertThat(stored.getPasswordHash()).doesNotContain(TestUsers.PASSWORD);
        assertThat(refreshTokenRepository.findByTokenHash(s.refreshToken())).isEmpty();
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        Session s = users().register("First");
        postJson(
                        "/api/auth/register",
                        Map.of(
                                "email", s.email().toUpperCase(),
                                "password", TestUsers.PASSWORD,
                                "username", "other_" + UUID.randomUUID().toString().substring(0, 8),
                                "name", "Second"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void duplicateUsernameIsRejected() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        users().register("one_" + suffix + "@example.com", "dup_" + suffix, "One");
        postJson(
                        "/api/auth/register",
                        Map.of(
                                "email", "two_" + suffix + "@example.com",
                                "password", TestUsers.PASSWORD,
                                "username", "DUP_" + suffix,
                                "name", "Two"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.username").exists());
    }

    @Test
    void registerValidationReturnsFieldLevelErrors() throws Exception {
        postJson(
                        "/api/auth/register",
                        Map.of("email", "not-an-email", "password", "short", "username", "a b", "name", ""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void malformedJsonIsAValidationError() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void loginSucceedsWithCorrectCredentials() throws Exception {
        Session s = users().register("Login User");
        postJson("/api/auth/login", Map.of("email", s.email(), "password", TestUsers.PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.id").value(s.userId().toString()));
    }

    @Test
    void loginFailuresAreIndistinguishable() throws Exception {
        Session s = users().register("Login User");
        postJson("/api/auth/login", Map.of("email", s.email(), "password", "wrong-password-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        postJson("/api/auth/login", Map.of("email", "nobody@example.com", "password", "wrong-password-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void refreshRotatesTheToken() throws Exception {
        Session s = users().register("Rotate");
        String body =
                postJson("/api/auth/refresh", Map.of("refreshToken", s.refreshToken()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode node = json.readTree(body);
        assertThat(node.get("refreshToken").asText()).isNotEqualTo(s.refreshToken());

        // the new access token works
        mvc.perform(
                        get("/api/me/profile")
                                .header("Authorization", "Bearer " + node.get("accessToken").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void reusingARotatedRefreshTokenRevokesEverySession() throws Exception {
        Session s = users().register("Replay");
        String rotated =
                postJson("/api/auth/refresh", Map.of("refreshToken", s.refreshToken()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String newRefresh = json.readTree(rotated).get("refreshToken").asText();

        // replaying the old token is treated as theft
        postJson("/api/auth/refresh", Map.of("refreshToken", s.refreshToken()))
                .andExpect(status().isUnauthorized());

        // ... which also kills the legitimately rotated token
        postJson("/api/auth/refresh", Map.of("refreshToken", newRefresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        Session s = users().register("Logout");
        postJson("/api/auth/logout", Map.of("refreshToken", s.refreshToken()))
                .andExpect(status().isNoContent());
        postJson("/api/auth/refresh", Map.of("refreshToken", s.refreshToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownRefreshTokenIsRejected() throws Exception {
        postJson("/api/auth/refresh", Map.of("refreshToken", "does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void protectedEndpointsRequireAValidToken() throws Exception {
        mvc.perform(get("/api/me/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/me/profile").header("Authorization", "Bearer garbage.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointsAreForbiddenForNormalUsers() throws Exception {
        Session s = users().register("Not Admin");
        mvc.perform(get("/api/admin/anything").header("Authorization", s.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
