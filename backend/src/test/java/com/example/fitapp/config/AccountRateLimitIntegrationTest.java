package com.example.fitapp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers.Session;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Guessing the password through the delete-account endpoint is throttled like logging in. */
@TestPropertySource(properties = "app.rate-limit.auth-per-minute=4")
class AccountRateLimitIntegrationTest extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    private ResultActions deleteWith(Session s, String password) throws Exception {
        return mvc.perform(
                MockMvcRequestBuilders.delete("/api/me")
                        .header("Authorization", s.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("password", password))));
    }

    @Test
    void wrongPasswordsAreThrottledAndTheAccountSurvives() throws Exception {
        Session s = users().register("Guessed"); // registering used one of the four tries
        deleteWith(s, "guess-1").andExpect(status().isBadRequest());
        deleteWith(s, "guess-2").andExpect(status().isBadRequest());
        deleteWith(s, "guess-3").andExpect(status().isBadRequest());

        deleteWith(s, "guess-4").andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After")).andExpect(jsonPath("$.code").value("LIMIT_REACHED"));

        assertThat(jdbc.queryForObject("select count(*) from users where id = ?", Integer.class, s.userId())).isEqualTo(1);
    }
}
