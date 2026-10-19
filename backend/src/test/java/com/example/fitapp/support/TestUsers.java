package com.example.fitapp.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Registers throwaway users through the real HTTP API. */
public class TestUsers {

    public record Session(UUID userId, String email, String accessToken, String refreshToken) {
        public String bearer() {
            return "Bearer " + accessToken;
        }
    }

    public static final String PASSWORD = "correct-horse-battery";

    private final MockMvc mvc;
    private final ObjectMapper json;

    public TestUsers(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    public Session register(String name) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return register("user_" + suffix + "@example.com", "u_" + suffix, name);
    }

    public Session register(String email, String username, String name) throws Exception {
        String body =
                json.writeValueAsString(
                        Map.of(
                                "email", email,
                                "password", PASSWORD,
                                "username", username,
                                "name", name));
        String response =
                mvc.perform(
                                post("/api/auth/register")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return toSession(json.readTree(response));
    }

    public static Session toSession(JsonNode node) {
        return new Session(
                UUID.fromString(node.get("user").get("id").asText()),
                node.get("user").get("email").asText(),
                node.get("accessToken").asText(),
                node.get("refreshToken").asText());
    }
}
