package com.example.fitapp.support;

import com.example.fitapp.support.TestUsers.Session;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Boots the full app against a throwaway PostgreSQL 16 container (shared by all test classes).
 * Flyway runs the real migrations, so entity/schema drift shows up here. Needs Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "app.jwt.secret=test-secret-test-secret-test-secret-0123456789",
            "app.rate-limit.auth-per-minute=100000",
            "app.rate-limit.search-per-minute=100000",
            "app.rate-limit.expensive-per-minute=100000",
            // A key in the developer's environment or .env must never make a test call the real API.
            "app.anthropic.api-key="
        })
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;

    protected TestUsers users() {
        return new TestUsers(mvc, json);
    }

    protected ResultActions get(Session s, String path) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(path).header("Authorization", s.bearer()));
    }

    protected ResultActions post(Session s, String path, Object body) throws Exception {
        return mvc.perform(
                MockMvcRequestBuilders.post(path)
                        .header("Authorization", s.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)));
    }

    protected ResultActions put(Session s, String path, Object body) throws Exception {
        return mvc.perform(
                MockMvcRequestBuilders.put(path)
                        .header("Authorization", s.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)));
    }

    protected ResultActions delete(Session s, String path) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.delete(path).header("Authorization", s.bearer()));
    }

    protected JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** Looks up a built-in exercise id by its exact name. */
    protected UUID exerciseId(Session s, String name) throws Exception {
        JsonNode page = body(get(s, "/api/exercises?size=100&q=" + name.replace(" ", "%20")));
        for (JsonNode item : page.get("items")) {
            if (item.get("name").asText().equals(name)) {
                return UUID.fromString(item.get("id").asText());
            }
        }
        throw new AssertionError("exercise not found: " + name);
    }
}
