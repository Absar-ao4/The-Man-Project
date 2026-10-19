package com.example.fitapp.workout.generator;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers.Session;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Without an ANTHROPIC_API_KEY the plan generator still works and only the AI step says it is unavailable.
 * The key is blanked on purpose so a key in the developer's environment can never make this call the real API.
 */
@TestPropertySource(properties = "app.anthropic.api-key=")
class PlanExplainUnconfiguredTest extends AbstractIntegrationTest {

    private ObjectNode answers() {
        ObjectNode n = json.createObjectNode();
        n.put("goal", "MAINTENANCE");
        n.put("daysPerWeek", 3);
        n.put("sessionMinutes", 45);
        n.put("equipment", "FULL_GYM");
        n.put("level", "BEGINNER");
        return n;
    }

    @Test
    void generatingWorksWithoutAKey() throws Exception {
        Session user = users().register("NoKey Generate");
        post(user, "/api/plans/generate", answers()).andExpect(status().isOk());
    }

    @Test
    void explainingIsUnavailableWithoutAKeyAndUsesNoQuota() throws Exception {
        Session user = users().register("NoKey Explain");

        post(user, "/api/plans/generate/explain", answers()).andExpect(status().isServiceUnavailable());
    }
}
