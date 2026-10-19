package com.example.fitapp.workout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers.Session;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoutineIntegrationTest extends AbstractIntegrationTest {

    private static Map<String, Object> row(UUID exerciseId, int order, int sets, int min, int max) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("exerciseId", exerciseId.toString());
        m.put("orderIndex", order);
        m.put("targetSets", sets);
        m.put("repMin", min);
        m.put("repMax", max);
        m.put("restSeconds", 90);
        return m;
    }

    private static Map<String, Object> routine(String name, List<Map<String, Object>> exercises) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("notes", "");
        m.put("exercises", exercises);
        return m;
    }

    @Test
    void createReadUpdateAndDeleteARoutine() throws Exception {
        Session s = users().register("Routine Owner");
        UUID bench = exerciseId(s, "Barbell Bench Press");
        UUID barbellRow = exerciseId(s, "Barbell Row");

        JsonNode created =
                body(
                        post(s, "/api/routines", routine("Push day", List.of(row(bench, 0, 3, 6, 10))))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.exercises[0].exerciseName").value("Barbell Bench Press")));
        String id = created.get("id").asText();

        // update: keep the bench row (same id), drop nothing, add a row
        String benchRowId = created.get("exercises").get(0).get("id").asText();
        Map<String, Object> keepBench = row(bench, 0, 4, 5, 8);
        keepBench.put("id", benchRowId);
        put(s, "/api/routines/" + id, routine("Push day v2", List.of(keepBench, row(barbellRow, 1, 3, 8, 12))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Push day v2"))
                .andExpect(jsonPath("$.exercises.length()").value(2))
                .andExpect(jsonPath("$.exercises[0].id").value(benchRowId))
                .andExpect(jsonPath("$.exercises[0].targetSets").value(4));

        // update again dropping the first row: it disappears from reads
        put(s, "/api/routines/" + id, routine("Push day v3", List.of(row(barbellRow, 0, 3, 8, 12))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercises.length()").value(1))
                .andExpect(jsonPath("$.exercises[0].exerciseName").value("Barbell Row"));

        assertThat(body(get(s, "/api/routines"))).hasSize(1);

        delete(s, "/api/routines/" + id).andExpect(status().isNoContent());
        get(s, "/api/routines/" + id).andExpect(status().isNotFound());
        assertThat(body(get(s, "/api/routines"))).isEmpty();
    }

    @Test
    void postingTheSameIdTwiceUpdatesInsteadOfDuplicating() throws Exception {
        Session s = users().register("Idempotent");
        UUID bench = exerciseId(s, "Barbell Bench Press");
        UUID id = UUID.randomUUID();
        Map<String, Object> req = routine("Retry me", List.of(row(bench, 0, 3, 6, 10)));
        req.put("id", id.toString());

        post(s, "/api/routines", req).andExpect(status().isCreated());
        post(s, "/api/routines", req).andExpect(status().isCreated());

        assertThat(body(get(s, "/api/routines"))).hasSize(1);
    }

    @Test
    void routinesArePrivateToTheirOwner() throws Exception {
        Session alice = users().register("Alice");
        Session bob = users().register("Bob");
        UUID bench = exerciseId(alice, "Barbell Bench Press");
        String id =
                body(post(alice, "/api/routines", routine("Alice only", List.of(row(bench, 0, 3, 6, 10)))))
                        .get("id")
                        .asText();

        get(bob, "/api/routines/" + id).andExpect(status().isNotFound());
        put(bob, "/api/routines/" + id, routine("Hijack", List.of())).andExpect(status().isNotFound());
        delete(bob, "/api/routines/" + id).andExpect(status().isNotFound());
        assertThat(body(get(bob, "/api/routines"))).isEmpty();

        Map<String, Object> clash = routine("Bob takes the id", List.of());
        clash.put("id", id);
        post(bob, "/api/routines", clash).andExpect(status().isConflict());

        // Alice's routine is untouched
        get(alice, "/api/routines/" + id).andExpect(jsonPath("$.name").value("Alice only"));
    }

    @Test
    void cannotReferenceSomeoneElsesCustomExercise() throws Exception {
        Session alice = users().register("Alice");
        Session bob = users().register("Bob");
        Map<String, Object> custom = new LinkedHashMap<>();
        custom.put("name", "Alice Special");
        custom.put("primaryMuscle", "BACK");
        custom.put("equipment", "CABLE");
        UUID aliceExercise = UUID.fromString(body(post(alice, "/api/exercises", custom)).get("id").asText());

        post(bob, "/api/routines", routine("Sneaky", List.of(row(aliceExercise, 0, 3, 8, 12))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void validatesRepRangeAndFields() throws Exception {
        Session s = users().register("Validator");
        UUID bench = exerciseId(s, "Barbell Bench Press");

        post(s, "/api/routines", routine("Bad reps", List.of(row(bench, 0, 3, 12, 6))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.exercises").exists());

        post(s, "/api/routines", routine("", List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());

        List<Map<String, Object>> zeroSets = new ArrayList<>();
        zeroSets.add(row(bench, 0, 0, 6, 10));
        post(s, "/api/routines", routine("Zero sets", zeroSets)).andExpect(status().isBadRequest());
    }

    @Test
    void routinesRequireAuthentication() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/routines"))
                .andExpect(status().isUnauthorized());
    }
}
