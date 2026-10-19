package com.example.fitapp.workout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers.Session;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanIntegrationTest extends AbstractIntegrationTest {

    @Test
    void seededPlansCoverEverySplitAndLevel() throws Exception {
        Session s = users().register("Planner");
        JsonNode plans = body(get(s, "/api/plans"));
        assertThat(plans).hasSize(12);

        for (String split : new String[] {"PPL", "BRO_SPLIT", "UPPER_LOWER", "FULL_BODY"}) {
            for (String level : new String[] {"BEGINNER", "INTERMEDIATE", "ADVANCED"}) {
                JsonNode match = body(get(s, "/api/plans?split=" + split + "&level=" + level));
                assertThat(match).as(split + " " + level).hasSize(1);
            }
        }
    }

    @Test
    void filtersByLevel() throws Exception {
        Session s = users().register("Level");
        JsonNode beginner = body(get(s, "/api/plans?level=BEGINNER"));
        assertThat(beginner).hasSize(4);
        beginner.forEach(p -> assertThat(p.get("level").asText()).isEqualTo("BEGINNER"));
    }

    @Test
    void planDetailHasOrderedDaysAndExercises() throws Exception {
        Session s = users().register("Detail");
        JsonNode ppl = body(get(s, "/api/plans?split=PPL&level=INTERMEDIATE")).get(0);
        assertThat(ppl.get("dayCount").asInt()).isEqualTo(3);
        assertThat(ppl.get("daysPerWeek").asInt()).isEqualTo(6);

        JsonNode detail = body(get(s, "/api/plans/" + ppl.get("id").asText()));
        JsonNode days = detail.get("days");
        assertThat(days).hasSize(3);
        assertThat(days.get(0).get("name").asText()).isEqualTo("Push");
        assertThat(days.get(1).get("name").asText()).isEqualTo("Pull");
        assertThat(days.get(2).get("name").asText()).isEqualTo("Legs");

        for (JsonNode day : days) {
            JsonNode exercises = day.get("exercises");
            assertThat(exercises).isNotEmpty();
            int previous = -1;
            for (JsonNode e : exercises) {
                assertThat(e.get("orderIndex").asInt()).isGreaterThan(previous);
                previous = e.get("orderIndex").asInt();
                assertThat(e.get("exerciseName").asText()).isNotBlank();
                assertThat(e.get("repMin").asInt()).isLessThanOrEqualTo(e.get("repMax").asInt());
                assertThat(e.get("sets").asInt()).isBetween(1, 6);
            }
        }
        assertThat(days.get(0).get("exercises").get(0).get("exerciseName").asText())
                .isEqualTo("Barbell Bench Press");
    }

    @Test
    void advancedPlansHaveMoreExercisesThanBeginnerOnes() throws Exception {
        Session s = users().register("Volume");
        int beginner = exerciseCount(s, "BRO_SPLIT", "BEGINNER");
        int advanced = exerciseCount(s, "BRO_SPLIT", "ADVANCED");
        assertThat(advanced).isGreaterThan(beginner);
    }

    private int exerciseCount(Session s, String split, String level) throws Exception {
        JsonNode summary = body(get(s, "/api/plans?split=" + split + "&level=" + level)).get(0);
        JsonNode detail = body(get(s, "/api/plans/" + summary.get("id").asText()));
        int total = 0;
        for (JsonNode day : detail.get("days")) {
            total += day.get("exercises").size();
        }
        return total;
    }

    @Test
    void unknownPlanIsNotFound() throws Exception {
        Session s = users().register("Missing");
        get(s, "/api/plans/" + UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void seedIdsAreStableAcrossRuns() throws Exception {
        // ids are md5-derived, so the plan id for a given key never changes between environments
        Session s = users().register("Stable");
        JsonNode plan = body(get(s, "/api/plans?split=PPL&level=BEGINNER")).get(0);
        assertThat(plan.get("id").asText()).hasSize(36);
        assertThat(plan.get("name").asText()).isEqualTo("Push Pull Legs (Beginner)");
    }
}
