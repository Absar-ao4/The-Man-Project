package com.example.fitapp.config;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers.Session;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Floods of the paid or heavy endpoints are throttled per client address, all in one bucket; everyday endpoints are not.
 * One test method on purpose: the bucket belongs to the client address, so a second test would start with it spent.
 */
@TestPropertySource(properties = "app.rate-limit.expensive-per-minute=2")
class ExpensiveRateLimitIntegrationTest extends AbstractIntegrationTest {

    @Test
    void theThirdExpensiveCallInAMinuteIsThrottledWhateverItIsAndOtherEndpointsAreNot() throws Exception {
        Session s = users().register("Flooder");

        // No daily targets, so this one is a 400: what counts is that the call got through to the application.
        post(s, "/api/ai/meal-suggestions", Map.of()).andExpect(status().isBadRequest());
        get(s, "/api/me/export").andExpect(status().isOk());
        post(s, "/api/ai/meal-suggestions", Map.of())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("LIMIT_REACHED"));
        get(s, "/api/ai/workout-recommendations").andExpect(status().isTooManyRequests());

        get(s, "/api/articles").andExpect(status().isOk());
        get(s, "/api/badges").andExpect(status().isOk());
    }
}
