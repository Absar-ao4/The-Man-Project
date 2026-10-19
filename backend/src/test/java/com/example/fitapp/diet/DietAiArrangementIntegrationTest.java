package com.example.fitapp.diet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.diet.DietOptimizer.Line;
import com.example.fitapp.diet.MealArranger.ArrangedMeal;
import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers.Session;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The AI arrangement step with the model replaced by a mock: quota, fallback to rules, refund on failure. */
@TestPropertySource(properties = {"app.anthropic.api-key=test-key", "app.ai.daily-limits.diet-arrange=1"})
class DietAiArrangementIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean MealArranger arranger;

    @BeforeEach
    void aiWorks() {
        Mockito.reset(arranger);
        when(arranger.arrange(any(), anyInt(), any()))
                .thenAnswer(
                        inv -> {
                            List<Line> lines = inv.getArgument(0);
                            int meals = inv.getArgument(1);
                            List<ArrangedMeal> rules = RulesMealArranger.arrange(lines, meals);
                            List<ArrangedMeal> ai = new ArrayList<>();
                            for (ArrangedMeal m : rules) {
                                ai.add(new ArrangedMeal("AI " + m.title(), "Cook it simply.", m.lines()));
                            }
                            return ai;
                        });
    }

    private Session user(String name) throws Exception {
        Session s = users().register(name);
        Map<String, Object> targets = new LinkedHashMap<>();
        targets.put("calories", 2200);
        targets.put("proteinG", 110);
        targets.put("carbsG", 260);
        targets.put("fatG", 70);
        targets.put("waterGlasses", 8);
        put(s, "/api/me/targets", targets).andExpect(status().isOk());
        return s;
    }

    private JsonNode plan(Session s) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("budget", 200);
        m.put("currency", "INR");
        m.put("dietType", "VEGETARIAN");
        m.put("mealsPerDay", 3);
        m.put("cuisine", "INDIAN");
        return body(post(s, "/api/diet/plans", m).andExpect(status().isCreated()));
    }

    private List<String> notes(JsonNode plan) {
        List<String> notes = new ArrayList<>();
        plan.get("notes").forEach(n -> notes.add(n.asText()));
        return notes;
    }

    @Test
    void theAiArrangementTitlesAndRecipesAreStored() throws Exception {
        Session s = user("Ai Planner");

        JsonNode plan = plan(s);

        assertThat(plan.get("arrangedBy").asText()).isEqualTo("AI");
        assertThat(plan.get("meals").get(0).get("title").asText()).isEqualTo("AI Breakfast");
        assertThat(plan.get("meals").get(0).get("recipe").asText()).isEqualTo("Cook it simply.");
        // And it reads back the same.
        JsonNode read = body(get(s, "/api/diet/plans/" + plan.get("id").asText()).andExpect(status().isOk()));
        assertThat(read.get("meals").get(2).get("title").asText()).isEqualTo("AI Dinner");
        assertThat(read.get("arrangedBy").asText()).isEqualTo("AI");
    }

    @Test
    void thePerUserDailyLimitFallsBackToRulesWithANote() throws Exception {
        Session s = user("Limited Planner");

        JsonNode first = plan(s);
        JsonNode second = plan(s);

        assertThat(first.get("arrangedBy").asText()).isEqualTo("AI");
        assertThat(second.get("arrangedBy").asText()).isEqualTo("RULES");
        assertThat(second.get("meals").get(0).get("title").asText()).isEqualTo("Breakfast");
        assertThat(notes(second)).anyMatch(n -> n.contains("used today's AI meal arrangements"));
        verify(arranger, times(1)).arrange(any(), anyInt(), any());
    }

    @Test
    void oneUsersLimitDoesNotAffectAnother() throws Exception {
        Session a = user("Planner A");
        Session b = user("Planner B");
        plan(a);

        assertThat(plan(b).get("arrangedBy").asText()).isEqualTo("AI");
    }

    @Test
    void aFailedArrangementFallsBackToRulesAndGivesTheUseBack() throws Exception {
        Session s = user("Unlucky Planner");
        when(arranger.arrange(any(), anyInt(), any())).thenThrow(new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "down"));

        JsonNode failed = plan(s);

        assertThat(failed.get("arrangedBy").asText()).isEqualTo("RULES");
        assertThat(notes(failed)).anyMatch(n -> n.contains("not available"));
        assertThat(failed.get("meals").size()).isEqualTo(3);

        // The failed try was given back, so the single daily use is still there.
        when(arranger.arrange(any(), anyInt(), any()))
                .thenAnswer(inv -> RulesMealArranger.arrange(inv.getArgument(0), inv.getArgument(1)));
        assertThat(plan(s).get("arrangedBy").asText()).isEqualTo("AI");
    }

    @Test
    void anArrangementWithTheWrongNumberOfMealsIsNotUsed() throws Exception {
        Session s = user("Wrong Count");
        when(arranger.arrange(any(), anyInt(), any())).thenReturn(List.of());

        JsonNode plan = plan(s);

        assertThat(plan.get("arrangedBy").asText()).isEqualTo("RULES");
        assertThat(plan.get("meals").size()).isEqualTo(3);
    }

    @Test
    void theArrangerIsNotCalledWhenThePlanCannotBeBuilt() throws Exception {
        Session noTargets = users().register("No Targets Ai");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("budget", 200);
        m.put("currency", "INR");
        m.put("dietType", "VEGETARIAN");
        m.put("mealsPerDay", 3);

        post(noTargets, "/api/diet/plans", m).andExpect(status().isBadRequest());

        verify(arranger, never()).arrange(any(), anyInt(), any());
    }
}
